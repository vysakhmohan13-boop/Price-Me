package com.example.pricecompare

import kotlin.math.roundToInt

// --- Unit & Quantity Normalization ---
enum class BaseUnit { GRAM, MILLILITER, PIECE, UNKNOWN }

data class NormalizedQuantity(
    val baseValue: Double, // Value in grams, milliliters, or pieces
    val unit: BaseUnit
)

object UnitNormalizer {
    fun normalize(quantity: Double, rawUnit: String): NormalizedQuantity {
        val u = rawUnit.trim().lowercase()
        return when {
            u in listOf("kg", "kilogram", "kilograms") -> NormalizedQuantity(quantity * 1000.0, BaseUnit.GRAM)
            u in listOf("g", "gm", "gram", "grams") -> NormalizedQuantity(quantity, BaseUnit.GRAM)
            u in listOf("l", "ltr", "liter", "liters", "litre") -> NormalizedQuantity(quantity * 1000.0, BaseUnit.MILLILITER)
            u in listOf("ml", "milliliter", "milliliters") -> NormalizedQuantity(quantity, BaseUnit.MILLILITER)
            u in listOf("pc", "pcs", "pack", "packs", "unit", "units") -> NormalizedQuantity(quantity, BaseUnit.PIECE)
            else -> NormalizedQuantity(quantity, BaseUnit.UNKNOWN)
        }
    }

    fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isConservativeMatch(queryTitle: String, offerTitle: String): Boolean {
        val q = normalizeTitle(queryTitle)
        val o = normalizeTitle(offerTitle)
        if (q == o) return true
        val qTokens = q.split(" ").filter { it.length > 2 }
        val oTokens = o.split(" ").filter { it.length > 2 }
        return qTokens.isNotEmpty() && qTokens.all { oTokens.contains(it) }
    }
}

// --- Domain Models ---
data class CartItem(
    val id: String,
    val name: String,
    val brand: String = "",
    val unitQuantity: Double,
    val unit: String,
    val count: Int = 1
) {
    val normalizedQty: NormalizedQuantity = UnitNormalizer.normalize(unitQuantity, unit)
}

data class ProviderPolicy(
    val storeName: String,
    val deliveryFee: Double,
    val handlingFee: Double = 0.0,
    val minFreeDeliveryThreshold: Double
)

data class StoreOffer(
    val storeName: String,
    val rawTitle: String,
    val price: Double, // Price for 1 unit of the item
    val isAvailable: Boolean = true,
    val unitQuantity: Double,
    val unit: String
) {
    val normalizedQty: NormalizedQuantity = UnitNormalizer.normalize(unitQuantity, unit)
}

data class SplitCartResult(
    val strategy: String,
    val totalProductCost: Double,
    val totalDeliveryFee: Double,
    val totalHandlingFee: Double,
    val grandTotal: Double,
    val storesUsed: List<String>,
    val storeBreakdown: Map<String, StoreCartSummary>,
    val cheapestSingleStoreTotal: Double?,
    val cheapestSingleStoreName: String?,
    val absoluteSavings: Double,
    val percentageSavings: Double
)

data class StoreCartSummary(
    val storeName: String,
    val items: List<Pair<CartItem, Double>>, // Item to item total cost (price * count)
    val itemSubtotal: Double,
    val deliveryFee: Double,
    val handlingFee: Double,
    val amountNeededForFreeDelivery: Double,
    val storeLandedTotal: Double
)

// --- Split-Cart Optimization Engine ---
object CartOptimizer {

    fun optimize(
        cart: List<CartItem>,
        offersMap: Map<CartItem, List<StoreOffer>>,
        policies: Map<String, ProviderPolicy>
    ): SplitCartResult {
        if (cart.isEmpty()) {
            return emptyResult()
        }

        val availableStores = policies.keys.toList()
        var bestGrandTotal = Double.MAX_VALUE
        var bestStoreBreakdown: Map<String, StoreCartSummary> = emptyMap()
        var bestStoresUsed: List<String> = emptyList()

        // Generate combinations of store assignments
        // Evaluate all store combinations (Power set of available stores excluding empty)
        val storeSubsets = generateSubsets(availableStores).filter { it.isNotEmpty() }

        for (subset in storeSubsets) {
            // For a given subset of stores, find the assignment of each item to the cheapest store in the subset
            val assignment = mutableMapOf<CartItem, StoreOffer>()
            var isCombinationValid = true

            for (item in cart) {
                val validOffers = offersMap[item]?.filter { offer ->
                    offer.isAvailable &&
                            subset.contains(offer.storeName) &&
                            offer.normalizedQty == item.normalizedQty &&
                            UnitNormalizer.isConservativeMatch(item.name, offer.rawTitle)
                } ?: emptyList()

                val cheapestOffer = validOffers.minByOrNull { it.price }
                if (cheapestOffer == null) {
                    isCombinationValid = false
                    break // This subset cannot fulfill all items
                } else {
                    assignment[item] = cheapestOffer
                }
            }

            if (!isCombinationValid) continue

            // Calculate fees per store used in this assignment
            val currentBreakdown = mutableMapOf<String, StoreCartSummary>()
            var currentGrandTotal = 0.0

            val itemsByStore = assignment.entries.groupBy { it.value.storeName }

            for ((storeName, entries) in itemsByStore) {
                val policy = policies[storeName] ?: ProviderPolicy(storeName, 0.0, 0.0, 0.0)
                val storeItems = entries.map { (item, offer) ->
                    val totalItemCost = offer.price * item.count
                    Pair(item, totalItemCost)
                }
                val subtotal = storeItems.sumOf { it.second }
                val deliveryFee = if (subtotal >= policy.minFreeDeliveryThreshold) 0.0 else policy.deliveryFee
                val handlingFee = policy.handlingFee
                val neededForFreeDelivery = if (subtotal < policy.minFreeDeliveryThreshold) {
                    policy.minFreeDeliveryThreshold - subtotal
                } else 0.0

                val storeLanded = subtotal + deliveryFee + handlingFee

                currentBreakdown[storeName] = StoreCartSummary(
                    storeName = storeName,
                    items = storeItems,
                    itemSubtotal = subtotal,
                    deliveryFee = deliveryFee,
                    handlingFee = handlingFee,
                    amountNeededForFreeDelivery = neededForFreeDelivery,
                    storeLandedTotal = storeLanded
                )

                currentGrandTotal += storeLanded
            }

            if (currentGrandTotal < bestGrandTotal) {
                bestGrandTotal = currentGrandTotal
                bestStoreBreakdown = currentBreakdown
                bestStoresUsed = itemsByStore.keys.toList()
            }
        }

        // Single Store Comparison
        var cheapestSingleStoreTotal: Double? = null
        var cheapestSingleStoreName: String? = null

        for (storeName in availableStores) {
            val singleBreakdown = bestStoreBreakdown.filter { it.key == storeName }
            if (bestStoresUsed.size == 1 && bestStoresUsed.contains(storeName)) {
                if (cheapestSingleStoreTotal == null || bestGrandTotal < cheapestSingleStoreTotal) {
                    cheapestSingleStoreTotal = bestGrandTotal
                    cheapestSingleStoreName = storeName
                }
            } else {
                // Check if store can fulfill single store cart
                val canFulfill = cart.all { item ->
                    offersMap[item]?.any { offer ->
                        offer.storeName == storeName && offer.isAvailable && offer.normalizedQty == item.normalizedQty
                    } == true
                }
                if (canFulfill) {
                    val subtotal = cart.sumOf { item ->
                        val offer = offersMap[item]!!.first { it.storeName == storeName }
                        offer.price * item.count
                    }
                    val policy = policies[storeName] ?: ProviderPolicy(storeName, 0.0, 0.0, 0.0)
                    val deliveryFee = if (subtotal >= policy.minFreeDeliveryThreshold) 0.0 else policy.deliveryFee
                    val total = subtotal + deliveryFee + policy.handlingFee
                    if (cheapestSingleStoreTotal == null || total < cheapestSingleStoreTotal) {
                        cheapestSingleStoreTotal = total
                        cheapestSingleStoreName = storeName
                    }
                }
            }
        }

        val totalProductCost = bestStoreBreakdown.values.sumOf { it.itemSubtotal }
        val totalDeliveryFee = bestStoreBreakdown.values.sumOf { it.deliveryFee }
        val totalHandlingFee = bestStoreBreakdown.values.sumOf { it.handlingFee }

        val absSavings = if (cheapestSingleStoreTotal != null && cheapestSingleStoreTotal > bestGrandTotal) {
            cheapestSingleStoreTotal - bestGrandTotal
        } else 0.0

        val pctSavings = if (cheapestSingleStoreTotal != null && cheapestSingleStoreTotal > 0.0) {
            ((absSavings / cheapestSingleStoreTotal) * 1000.0).roundToInt() / 10.0
        } else 0.0

        val strategyText = when {
            bestStoresUsed.isEmpty() -> "No store combination can fulfill this cart"
            bestStoresUsed.size == 1 -> "Single Store (${bestStoresUsed.first()})"
            else -> "Split Cart (${bestStoresUsed.joinToString(", ")})"
        }

        return SplitCartResult(
            strategy = strategyText,
            totalProductCost = totalProductCost,
            totalDeliveryFee = totalDeliveryFee,
            totalHandlingFee = totalHandlingFee,
            grandTotal = if (bestGrandTotal == Double.MAX_VALUE) 0.0 else bestGrandTotal,
            storesUsed = bestStoresUsed,
            storeBreakdown = bestStoreBreakdown,
            cheapestSingleStoreTotal = cheapestSingleStoreTotal,
            cheapestSingleStoreName = cheapestSingleStoreName,
            absoluteSavings = absSavings,
            percentageSavings = pctSavings
        )
    }

    private fun generateSubsets(list: List<String>): List<List<String>> {
        if (list.isEmpty()) return listOf(emptyList())
        val head = list.first()
        val tailSubsets = generateSubsets(list.drop(1))
        return tailSubsets + tailSubsets.map { listOf(head) + it }
    }

    private fun emptyResult() = SplitCartResult(
        strategy = "Empty Cart",
        totalProductCost = 0.0,
        totalDeliveryFee = 0.0,
        totalHandlingFee = 0.0,
        grandTotal = 0.0,
        storesUsed = emptyList(),
        storeBreakdown = emptyMap(),
        cheapestSingleStoreTotal = null,
        cheapestSingleStoreName = null,
        absoluteSavings = 0.0,
        percentageSavings = 0.0
    )
}