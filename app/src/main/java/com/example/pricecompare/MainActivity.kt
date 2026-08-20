package com.example.pricecompare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Data Models ---
data class ProductOffer(
    val storeName: String,
    val productName: String,
    val price: Double,
    val deliveryFee: Double,
    val minFreeDelivery: Double,
    val deliveryTimeMinutes: Int
)

data class CartOptimizationResult(
    val strategy: String,
    val totalProductCost: Double,
    val totalDeliveryFee: Double,
    val grandTotal: Double,
    val breakdown: List<String>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF6F6F6)
                ) {
                    QuickCommerceAggregatorScreen()
                }
            }
        }
    }
}

@Composable
fun QuickCommerceAggregatorScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var offers by remember { mutableStateOf<List<ProductOffer>>(emptyList()) }
    var optimizationResult by remember { mutableStateOf<CartOptimizationResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Quick-Commerce Aggregator",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E)
        )
        Text(
            text = "Blinkit • Zepto • Instamart • BigBasket",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search product (e.g. Milk 1L, Amul Butter)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (searchQuery.isNotBlank()) {
                    offers = fetchStoreOffers(searchQuery)
                    optimizationResult = calculateOptimalCart(offers)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Compare Across Platforms")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (optimizationResult != null) {
            OptimizationSummaryCard(result = optimizationResult!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(offers) { offer ->
                OfferCard(offer = offer)
            }
        }
    }
}

@Composable
fun OptimizationSummaryCard(result: CartOptimizationResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚡ Best Strategy: ${result.strategy}",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Items Total: ₹${result.totalProductCost}")
            Text("Delivery Fees: ₹${result.totalDeliveryFee}")
            Text(
                text = "Grand Total: ₹${result.grandTotal}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1B5E20)
            )
        }
    }
}

@Composable
fun OfferCard(offer: ProductOffer) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = offer.storeName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = offer.productName, fontSize = 13.sp, color = Color.Gray)
                Text(
                    text = "${offer.deliveryTimeMinutes} mins • Delivery: ₹${offer.deliveryFee} (Free over ₹${offer.minFreeDelivery})",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }
            Text(
                text = "₹${offer.price}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF00838F)
            )
        }
    }
}

// --- Provider Adapters & Optimizer Logic ---
fun fetchStoreOffers(query: String): List<ProductOffer> {
    return listOf(
        ProductOffer("Blinkit", "$query (Normalized)", 62.0, 15.0, 199.0, 11),
        ProductOffer("Zepto", "$query (Normalized)", 58.0, 25.0, 149.0, 8),
        ProductOffer("Swiggy Instamart", "$query (Normalized)", 60.0, 20.0, 199.0, 14),
        ProductOffer("BigBasket Now", "$query (Normalized)", 56.0, 30.0, 249.0, 22)
    )
}

fun calculateOptimalCart(offers: List<ProductOffer>): CartOptimizationResult {
    val bestSingleStore = offers.minByOrNull { it.price + it.deliveryFee }!!
    return CartOptimizationResult(
        strategy = "Single Store (${bestSingleStore.storeName})",
        totalProductCost = bestSingleStore.price,
        totalDeliveryFee = bestSingleStore.deliveryFee,
        grandTotal = bestSingleStore.price + bestSingleStore.deliveryFee,
        breakdown = listOf("${bestSingleStore.storeName}: ₹${bestSingleStore.price} + ₹${bestSingleStore.deliveryFee} Delivery")
    )
}