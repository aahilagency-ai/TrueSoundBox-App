package com.texhuman.truesoundbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val paymentList = mutableStateListOf<PaymentItem>()

    private val paymentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val amount = intent?.getStringExtra("amount") ?: return
            val payerName = intent.getStringExtra("payerName") ?: "UPI Customer"
            val appName = intent.getStringExtra("appName") ?: "UPI QR"
            val ts = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))

            paymentList.add(
                0,
                PaymentItem(
                    id = "TXN_$ts",
                    amount = amount,
                    payerName = payerName,
                    appName = appName,
                    timeFormatted = timeStr,
                    timestamp = ts
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SoundboxVoiceEngine.init(this)

        val filter = IntentFilter("com.texhuman.truesoundbox.NEW_PAYMENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, paymentReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(paymentReceiver, filter)
        }

        setContent {
            MaterialTheme {
                var currentUid by remember {
                    mutableStateOf(
                        FirebaseAuth.getInstance().currentUser?.uid
                            ?: SubscriptionManager.getCurrentUserId(this@MainActivity)
                    )
                }

                val subInfo by SubscriptionManager.subState.collectAsState()
                val isSyncDone by SubscriptionManager.isInitialSyncDone.collectAsState()

                LaunchedEffect(currentUid) {
                    if (currentUid.isNotBlank()) {
                        SubscriptionManager.restoreSubscriptionFromCloud(this@MainActivity)
                    }
                }

                when {
                    currentUid.isBlank() -> {
                        AuthScreen(onAuthSuccess = { newUid ->
                            currentUid = newUid
                        })
                    }
                    !isSyncDone && !subInfo.isUnlocked -> {
                        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF0066FF))
                                Spacer(Modifier.height(14.dp))
                                Text("Checking subscription...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                    !subInfo.isUnlocked -> {
                        PaywallScreen(
                            subInfo = subInfo,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                SubscriptionManager.resetOnLogout(this@MainActivity)
                                paymentList.clear()
                                currentUid = ""
                            }
                        )
                    }
                    else -> {
                        MainDashboardScreen(
                            payments = paymentList,
                            subInfo = subInfo,
                            onSimulatePayment = { amt, payer ->
                                SoundboxVoiceEngine.speakPayment(this@MainActivity, amt, payer)
                                val ts = System.currentTimeMillis()
                                paymentList.add(
                                    0,
                                    PaymentItem(
                                        id = "TEST_$ts",
                                        amount = amt,
                                        payerName = payer,
                                        appName = "Test Alert",
                                        timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts)),
                                        timestamp = ts
                                    )
                                )
                            },
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                SubscriptionManager.resetOnLogout(this@MainActivity)
                                paymentList.clear()
                                currentUid = ""
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val payId = razorpayPaymentId.orEmpty().ifBlank { paymentData?.paymentId.orEmpty() }
        val orderId = paymentData?.orderId.orEmpty()
        val sig = paymentData?.signature.orEmpty()
        SubscriptionManager.onPaymentSuccess(this, payId, orderId, sig)
        Toast.makeText(this, "Subscription Activated!", Toast.LENGTH_LONG).show()
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Payment Failed/Cancelled ($code)", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        try { unregisterReceiver(paymentReceiver) } catch (_: Exception) {}
        SoundboxVoiceEngine.shutdown()
        super.onDestroy()
    }
}
