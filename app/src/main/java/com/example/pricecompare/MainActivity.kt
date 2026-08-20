package com.example.pricecompare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF6F6F6)
                ) {
                    PriceCompareApp()
                }
            }
        }
    }
}

// --- Pre-populated Mock Catalog for Adding Items ---
val MOCK_CATALOG = listOf(
    CartItem("1", "Amul Butter", "Amul", 100.0, "g"),
    CartItem("2", "Toned Milk", "Nandini", 1.0, "L"),
    CartItem("3", "Whole Wheat Bread", "Britannia", 400.0, "g"),
    CartItem("4", "Farm Fresh Eggs", "Eggoz", 6.0, "pcs"),
    CartItem("5", "Aashirvaad Atta", "Aashirvaad", 5.0, "kg"),
    CartItem("6", "Sugar", "Madhur", 1.0, "kg")
)

val MOCK_POLICIES = mapOf(
    "Blinkit" to ProviderPolicy("Blinkit", deliveryFee = 15.0, handlingFee = 5.0, minFreeDeliveryThreshold = 200.0),
    "Zepto" to ProviderPolicy("Zepto", deliveryFee = 25.0, handlingFee = 2.0, minFreeDeliveryThreshold = 150.0),
    "Swiggy Instamart" to ProviderPolicy("Swiggy Instamart", deliveryFee = 20.0, handlingFee = 0.0, minFreeDeliveryThreshold = 250.0),
    "BigBasket" to ProviderPolicy("BigBasket", deliveryFee = 30.0, handlingFee = 0.0, minFreeDeliveryThreshold = 300.0)
)

@Composable
fun PriceCompareApp() {
    var pincode by remember { mutableStateOf("600001") }
    var locationStatus by remember { mutableStateOf("Location: Manual Pincode ($pincode)") }
    val cart = remember { mutableStateListOf<CartItem>() }
    var searchFilter by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SplitCartResult?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Transparency & Location Header
        HeaderSection(
            locationStatus = locationStatus,
            pincode = pincode,
            onPincodeChange = { 
                pincode = it 
                locationStatus = "Location: Manual Pincode ($it)"
            },
            onGPSClick = { locationStatus = "Location: GPS (Lat: 13.08, Long: 80.27)" }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Product Search & Add
            item {
                Text("Add Products to Cart", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    label = { Text("Search catalog...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            val filteredCatalog = MOCK_CATALOG.filter { it.name.contains(searchFilter, ignoreCase = true) }
            items(filteredCatalog) { item ->
                CatalogItemRow(item = item, onAdd = {
                    val existingIndex = cart.indexOfFirst { it.id == item.id }
                    if (existingIndex >= 0) {
                        val existing = cart[existingIndex]
                        cart[existingIndex] = existing.copy(count = existing.count + 1)
                    } else {
                        cart.add(item.copy(count = 1))
                    }
                })
            }

            // Section 2: Active Cart Display
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Cart (${cart.sumOf { it.count }} items)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (cart.isNotEmpty()) {
                        TextButton(onClick = { cart.clear(); result = null }) {
                            Text("Clear All", color = Color.Red)
                        }
                    }
                }
            }

            if (cart.isEmpty()) {
                item {
                    Text("Your cart is empty. Add products above.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                items(cart) { cartItem ->
                    CartItemRow(
                        item = cartItem,
                        onIncrement = {
                            val idx = cart.indexOf(cartItem)
                            cart[idx] = cartItem.copy(count = cartItem.count + 1)
                        },
                        onDecrement = {
                            val idx = cart.indexOf(cartItem)
                            if (cartItem.count > 1) {
                                cart[idx] = cartItem.copy(count = cartItem.count - 1)
                            } else {
                                cart.removeAt(idx)
                            }
                        },
                        onRemove = { cart.remove(cartItem) }
                    )
                }

                item {
                    Button(
                        onClick = {
                            val offersMap = generateMockOffersForCart(cart)
                            result = CartOptimizer.optimize(cart, offersMap, MOCK_POLICIES)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Optimize Basket across 4 Stores", fontSize = 16.sp)
                    }
                }
            }

            // Section 3: Optimization Results
            if (result != null) {
                item {
                    OptimizationResultsCard(result = result!!)
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    locationStatus: String,
    pincode: String,
    onPincodeChange: (String) -> Unit,
    onGPSClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF212121))
            .padding(12.dp)
    ) {
        // Transparency Banner
        Surface(
            color = Color(0xFFFF9800),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = " DEMO / MOCK DATA MODE — Prices are generated for testing ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(locationStatus, color = Color.White, fontSize = 12.sp)
            OutlinedButton(onClick = onGPSClick) {
                Text("Use GPS", color = Color.White, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = pincode,
            onValueChange = onPincodeChange,
            label = { Text("Pincode", color = Color.LightGray) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White
            )
        )
    }
}

@Composable
fun CatalogItemRow(item: CartItem, onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("${item.unitQuantity} ${item.unit} • ${item.brand}", fontSize = 12.sp, color = Color.Gray)
            }
            Button(onClick = onAdd) {
                Text("+ Add")
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEFEF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("${item.unitQuantity} ${item.unit}", fontSize = 12.sp, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement) { Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Text("${item.count}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                IconButton(onClick = onIncrement) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onRemove) { Text("X", color = Color.Red) }
            }
        }
    }
}

@Composable
fun OptimizationResultsCard(result: SplitCartResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚡ Optimal Strategy: ${result.strategy}",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                fontSize = 18.sp
            )
            Text(
                text = "Total Landed Cost: ₹${result.grandTotal}",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color(0xFF1B5E20)
            )

            if (result.cheapestSingleStoreTotal != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cheapest Single Store (${result.cheapestSingleStoreName}): ₹${result.cheapestSingleStoreTotal}",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = "You Save: ₹${result.absoluteSavings} (${result.percentageSavings}%) by splitting",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFC62828)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Store Breakdown & Free Delivery Progress:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            result.storeBreakdown.forEach { (store, summary) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(store, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Subtotal: ₹${summary.itemSubtotal} | Delivery: ₹${summary.deliveryFee} | Handling: ₹${summary.handlingFee}")

                        if (summary.amountNeededForFreeDelivery > 0) {
                            Text(
                                " Add ₹${summary.amountNeededForFreeDelivery} more for FREE delivery",
                                color = Color(0xFFE65100),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(" FREE Delivery Unlocked!", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Helper: Generates realistic mock offers for cart items across 4 stores
fun generateMockOffersForCart(cart: List<CartItem>): Map<CartItem, List<StoreOffer>> {
    val map = mutableMapOf<CartItem, List<StoreOffer>>()
    cart.forEach { item ->
        val basePrice = when (item.id) {
            "1" -> 50.0  // Butter
            "2" -> 30.0  // Milk
            "3" -> 40.0  // Bread
            "4" -> 55.0  // Eggs
            "5" -> 220.0 // Atta
            else -> 45.0 // Sugar
        }
        map[item] = listOf(
            StoreOffer("Blinkit", item.name, basePrice, true, item.unitQuantity, item.unit),
            StoreOffer("Zepto", item.name, basePrice - 2.0, true, item.unitQuantity, item.unit),
            StoreOffer("Swiggy Instamart", item.name, basePrice + 1.0, true, item.unitQuantity, item.unit),
            StoreOffer("BigBasket", item.name, basePrice - 4.0, true, item.unitQuantity, item.unit)
        )
    }
    return map
}