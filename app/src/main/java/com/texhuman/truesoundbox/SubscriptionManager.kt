package com.texhuman.truesoundbox

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.razorpay.Checkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SubscriptionManager {
    private const val TAG = "TrueSoundBox_Sub"
    private const val PREFS_NAME = "TrueSoundBox_CleanPrefs"
    private const val BACKEND_URL = "https://truesoundbox-backend.onrender.com"
    private const val FALLBACK_RAZORPAY_KEY = "rzp_test_U2jAuJ5S0TRdsW"

    private val _subState = MutableStateFlow(SubscriptionInfo())
    val subState: StateFlow<SubscriptionInfo> = _subState.asStateFlow()

    private val _isInitialSyncDone = MutableStateFlow(false)
    val isInitialSyncDone: StateFlow<Boolean> = _isInitialSyncDone.asStateFlow()

    private var pendingPlanType: String = "trial"
    private var pendingAmountRupees: Int = 2

    fun getCurrentUserId(context: Context): String {
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (firebaseUid.isNotBlank()) return firebaseUid
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("user_uid", "").orEmpty()
    }

    fun loadLocalCache(context: Context) {
        val uid = getCurrentUserId(context)
        if (uid.isBlank()) {
            _subState.value = SubscriptionInfo()
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val status = prefs.getString("sub_status_$uid", "expired") ?: "expired"
        val expiry = prefs.getLong("sub_expiry_$uid", 0L)
        val usedTrial = prefs.getBoolean("sub_used_trial_$uid", false)
        val payId = prefs.getString("sub_pay_id_$uid", "") ?: ""

        _subState.value = SubscriptionInfo(
            status = status,
            expiryMillis = expiry,
            hasUsedTrial = usedTrial,
            lastPaymentId = payId
        )
    }

    private fun saveLocalCache(context: Context, uid: String, info: SubscriptionInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("sub_status_$uid", info.status)
            .putLong("sub_expiry_$uid", info.expiryMillis)
            .putBoolean("sub_used_trial_$uid", info.hasUsedTrial)
            .putString("sub_pay_id_$uid", info.lastPaymentId)
            .apply()
        _subState.value = info
    }

    suspend fun restoreSubscriptionFromCloud(context: Context) {
        val uid = getCurrentUserId(context)
        if (uid.isBlank()) {
            _isInitialSyncDone.value = true
            return
        }

        loadLocalCache(context)

        try {
            val db = FirebaseFirestore.getInstance()
            val collections = listOf("users", "merchants", "user")
            var bestInfo: SubscriptionInfo? = null

            for (col in collections) {
                val snap = db.collection(col).document(uid).get().await()
                if (snap.exists()) {
                    val rawStatus = (snap.getString("subscriptionStatus")
                        ?: snap.getString("status")
                        ?: "expired").lowercase()
                    val expiry = snap.getLong("subscriptionExpiryMillis")
                        ?: snap.getLong("subscriptionExpiry")
                        ?: 0L
                    val usedTrial = snap.getBoolean("hasUsedTrial") ?: false
                    val payId = snap.getString("lastPaymentId") ?: ""

                    val candidate = SubscriptionInfo(
                        status = rawStatus,
                        expiryMillis = expiry,
                        hasUsedTrial = usedTrial,
                        lastPaymentId = payId
                    )
                    if (bestInfo == null || candidate.expiryMillis > bestInfo.expiryMillis) {
                        bestInfo = candidate
                    }
                }
            }

            if (bestInfo != null) {
                saveLocalCache(context, uid, bestInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud restore error: ${e.message}")
        } finally {
            _isInitialSyncDone.value = true
        }
    }

    fun resetOnLogout(context: Context) {
        val uid = getCurrentUserId(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        if (uid.isNotBlank()) {
            _subState.value = SubscriptionInfo()
        }
        _isInitialSyncDone.value = false
    }

    fun startCheckout(activity: Activity, isTrial: Boolean) {
        val uid = getCurrentUserId(activity)
        if (uid.isBlank()) {
            Toast.makeText(activity, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        pendingPlanType = if (isTrial) "trial" else "monthly"
        pendingAmountRupees = if (isTrial) 2 else 49
        val expectedPaise = if (isTrial) 200 else 4900

        Toast.makeText(
            activity,
            if (isTrial) "Creating ₹2 Trial Order..." else "Creating ₹49 Monthly Order...",
            Toast.LENGTH_SHORT
        ).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$BACKEND_URL/api/v1/create-razorpay-order")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 25000
                    readTimeout = 25000
                    doOutput = true
                }

                val bodyJson = JSONObject().apply {
                    put("uid", uid)
                    put("planType", pendingPlanType)
                    put("isTrial", isTrial)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(bodyJson.toString()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                val respJson = JSONObject(respText)

                if (code in 200..299 && respJson.has("orderId")) {
                    val orderId = respJson.getString("orderId")
                    val serverAmount = respJson.optInt("amount", expectedPaise)
                    val keyId = respJson.optString("keyId", FALLBACK_RAZORPAY_KEY)

                    withContext(Dispatchers.Main) {
                        openRazorpaySheet(
                            activity = activity,
                            keyId = keyId,
                            orderId = orderId,
                            amountPaise = serverAmount,
                            isTrial = isTrial
                        )
                    }
                } else {
                    val err = respJson.optString("error", "Server error ($code)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Order failed: $err", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Order API error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openRazorpaySheet(
        activity: Activity,
        keyId: String,
        orderId: String,
        amountPaise: Int,
        isTrial: Boolean
    ) {
        try {
            val checkout = Checkout()
            checkout.setKeyID(keyId)

            val options = JSONObject().apply {
                put("name", "TrueSoundBox")
                put("description", if (isTrial) "3-Day Paid Trial (₹2)" else "Pro Monthly Plan (₹49/mo)")
                put("order_id", orderId)
                put("currency", "INR")
                put("amount", amountPaise)
                put("theme", JSONObject().apply {
                    put("color", "#0066FF")
                })
            }
            checkout.open(activity, options)
        } catch (e: Exception) {
            Toast.makeText(activity, "Checkout error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun onPaymentSuccess(
        context: Context,
        paymentId: String,
        orderId: String,
        signature: String
    ) {
        val uid = getCurrentUserId(context)
        val isTrial = pendingPlanType == "trial"
        val days = if (isTrial) 3L else 30L
        val status = if (isTrial) "trial" else "active"
        val expiryMillis = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L

        val newInfo = SubscriptionInfo(
            status = status,
            expiryMillis = expiryMillis,
            hasUsedTrial = true,
            lastPaymentId = paymentId
        )

        if (uid.isNotBlank()) {
            saveLocalCache(context, uid, newInfo)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (uid.isNotBlank()) {
                    val db = FirebaseFirestore.getInstance()
                    val payload = hashMapOf(
                        "uid" to uid,
                        "subscriptionStatus" to status,
                        "status" to status,
                        "isSubscribed" to true,
                        "hasUsedTrial" to true,
                        "subscriptionExpiryMillis" to expiryMillis,
                        "lastPaymentId" to paymentId,
                        "lastAmountPaid" to pendingAmountRupees
                    )
                    db.collection("users").document(uid).set(payload, SetOptions.merge()).await()
                    db.collection("merchants").document(uid).set(payload, SetOptions.merge()).await()
                }

                if (orderId.isNotBlank() && signature.isNotBlank()) {
                    val url = URL("$BACKEND_URL/api/v1/verify-razorpay-payment")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                    }
                    val verifyBody = JSONObject().apply {
                        put("uid", uid)
                        put("razorpay_order_id", orderId)
                        put("razorpay_payment_id", paymentId)
                        put("razorpay_signature", signature)
                        put("planType", pendingPlanType)
                        put("amountPaid", pendingAmountRupees)
                    }
                    OutputStreamWriter(conn.outputStream).use { it.write(verifyBody.toString()) }
                    conn.responseCode
                }
            } catch (e: Exception) {
                Log.e(TAG, "Post-payment sync error: ${e.message}")
            }
        }
    }
}
