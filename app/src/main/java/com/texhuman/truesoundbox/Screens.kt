package com.texhuman.truesoundbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

@Composable
fun AuthScreen(onAuthSuccess: (String) -> Unit) {
    val context = LocalContext.current
    var isSignup by remember { mutableStateOf(false) }
    var shopName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), Color(0xFFFFFFFF))))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF0066FF),
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("TrueSoundBox", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
        Text("Smart Merchant UPI Voice Alerts", fontSize = 13.sp, color = Color(0xFF64748B))
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { isSignup = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Login",
                            fontWeight = FontWeight.Bold,
                            color = if (!isSignup) Color(0xFF0066FF) else Color.Gray
                        )
                    }
                    TextButton(
                        onClick = { isSignup = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Create Account",
                            fontWeight = FontWeight.Bold,
                            color = if (isSignup) Color(0xFF0066FF) else Color.Gray
                        )
                    }
                }

                if (isSignup) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop / Business Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10) phone = it.filter { c -> c.isDigit() } },
                    label = { Text("10-Digit Mobile Number") },
                    prefix = { Text("+91 ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    enabled = !loading,
                    onClick = {
                        if (phone.length != 10 || password.length < 6) {
                            Toast.makeText(context, "Enter valid 10-digit phone & 6-char password", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        loading = true
                        val email = "${phone}@truesoundbox.app"
                        val auth = FirebaseAuth.getInstance()
                        val task = if (isSignup) {
                            auth.createUserWithEmailAndPassword(email, password)
                        } else {
                            auth.signInWithEmailAndPassword(email, password)
                        }
                        task.addOnCompleteListener { res ->
                            loading = false
                            if (res.isSuccessful) {
                                val uid = auth.currentUser?.uid.orEmpty()
                                val prefs = context.getSharedPreferences("TrueSoundBox_CleanPrefs", Context.MODE_PRIVATE)
                                prefs.edit()
                                    .putString("user_uid", uid)
                                    .putString("shop_name", shopName.ifBlank { "TrueSoundBox Merchant" })
                                    .putString("phone", "+91$phone")
                                    .apply()
                                onAuthSuccess(uid)
                            } else {
                                Toast.makeText(context, res.exception?.localizedMessage ?: "Auth failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF))
                ) {
                    Text(
                        if (loading) "Please wait..." else if (isSignup) "Create Account" else "Login",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PaywallScreen(
    subInfo: SubscriptionInfo,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 12.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!subInfo.hasUsedTrial) {
                        Button(
                            onClick = { activity?.let { SubscriptionManager.startCheckout(it, isTrial = true) } },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Start ₹2 Trial Now", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text("3 Days Trial • ₹49/mo from Day 4", fontSize = 10.sp, color = Color.White.copy(0.9f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { activity?.let { SubscriptionManager.startCheckout(it, isTrial = false) } },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF0066FF))
                    ) {
                        Text("Subscribe Full Monthly Plan • ₹49/mo", fontWeight = FontWeight.Bold, color = Color(0xFF0066FF))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLogout) {
                    Icon(Icons.Filled.Logout, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logout")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("TrueSoundBox Pro", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            Text("Instant Voice Alerts for GPay, PhonePe & Paytm", fontSize = 13.sp, color = Color(0xFF64748B))
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("₹199", fontSize = 18.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0066FF)) {
                    Text(
                        if (subInfo.hasUsedTrial) "₹49" else "₹2",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            listOf(
                "Instant Loud Voice Alerts in 10+ Languages",
                "Works with All UPI Apps (GPay, PhonePe, Paytm, BHIM)",
                "No Extra Hardware Box Required",
                "Cancel Anytime with 1 Click"
            ).forEach { feature ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF16A34A))
                        Spacer(Modifier.width(10.dp))
                        Text(feature, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun MainDashboardScreen(
    payments: List<PaymentItem>,
    subInfo: SubscriptionInfo,
    onSimulatePayment: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("TrueSoundBox_CleanPrefs", Context.MODE_PRIVATE) }
    val shopName = prefs.getString("shop_name", "My Shop") ?: "My Shop"
    var langCode by remember { mutableStateOf(prefs.getString("lang_code", "hi") ?: "hi") }
    var muted by remember { mutableStateOf(prefs.getBoolean("muted", false)) }

    val totalAmount = payments.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.BarChart, null) },
                    label = { Text("Reports") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF090D16)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TrueSoundBox", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text(shopName, fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF064E3B)) {
                                Text(
                                    if (subInfo.status == "trial") "TRIAL ACTIVE" else "PRO ACTIVE",
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text("Today's Collection", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                Text(
                                    "₹${"%.2f".format(Locale.US, totalAmount)}",
                                    color = Color.White,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { onSimulatePayment("100", "Rahul Sharma") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24))
                                ) {
                                    Icon(Icons.Filled.VolumeUp, null, tint = Color.Black)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Test Voice Alert (₹100)", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Text("Enable Notification Access for Auto Alerts", color = Color(0xFF38BDF8))
                        }
                    }

                    items(payments) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.payerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${item.appName} • ${item.timeFormatted}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text("+₹${item.amount}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color(0xFF16A34A))
                            }
                        }
                    }
                }

                1 -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Business Reports", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Verified Revenue", color = Color.Gray, fontSize = 12.sp)
                            Text("₹${"%.2f".format(Locale.US, totalAmount)}", fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text("Total Payments: ${payments.size}", fontSize = 13.sp)
                        }
                    }
                }

                2 -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mute All Voice Alerts", fontWeight = FontWeight.Bold)
                        Switch(checked = muted, onCheckedChange = {
                            muted = it
                            prefs.edit().putBoolean("muted", it).apply()
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            langCode = "hi"
                            prefs.edit().putString("lang_code", "hi").apply()
                        }) { Text("Hindi") }
                        Button(onClick = {
                            langCode = "en"
                            prefs.edit().putString("lang_code", "en").apply()
                        }) { Text("English") }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE4E6))
                    ) {
                        Text("Logout", color = Color(0xFFE11D48), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
