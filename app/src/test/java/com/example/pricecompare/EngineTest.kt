package com.example.pricecompare

import org.junit.Assert.*
import org.junit.Test

class EngineTest {

    private val policies = mapOf(
        "Blinkit" to ProviderPolicy("Blinkit", deliveryFee = 15.0, handlingFee = 5.0, minFreeDeliveryThreshold = 200.0),
        "Zepto" to ProviderPolicy("Zepto", deliveryFee = 25.0, handlingFee = 2.0, minFreeDeliveryThreshold = 150.0),
        "Instamart" to ProviderPolicy("Instamart", deliveryFee = 20.0, handlingFee = 0.0, minFreeDeliveryThreshold = 250.0)
    )

    @Test
    fun testUnitNormalization() {
        val kg = UnitNormalizer.normalize(1.0, "kg")
        val gram = UnitNormalizer.normalize(1000.0, "g")
        assertEquals(kg, gram)

        val liter = UnitNormalizer.normalize(1.0, "L")
        val ml = UnitNormalizer.normalize(1000.0, "ml")
        assertEquals(liter, ml)
    }

    @Test
    fun testConservativeMatching() {
        assertTrue(UnitNormalizer.isConservativeMatch("Amul Taaza Milk 1L", "Amul Taaza Milk"))
        assertFalse(UnitNormalizer.isConservativeMatch("Amul Taaza Milk 1L", "Nandini Milk"))
    }

    @Test
    fun testTwoItemCart_SplitIsCheaper() {
        val item1 = CartItem("1", "Amul Butter", unitQuantity = 100.0, unit = "g", count = 1)
        val item2 = CartItem("2", "Toned Milk", unitQuantity = 1.0, unit = "L", count = 1)
        val cart = listOf(item1, item2)

        val offersMap = mapOf(
            item1 to listOf(
                StoreOffer("Blinkit", "Amul Butter", price = 50.0, unitQuantity = 100.0, unit = "g"),
                StoreOffer("Zepto", "Amul Butter", price = 90.0, unitQuantity = 100.0, unit = "g")
            ),
            item2 to listOf(
                StoreOffer("Blinkit", "Toned Milk", price = 80.0, unitQuantity = 1.0, unit = "L"),
                StoreOffer("Zepto", "Toned Milk", price = 30.0, unitQuantity = 1.0, unit = "L")
            )
        )

        val result = CartOptimizer.optimize(cart, offersMap, policies)

        assertEquals(2, result.storesUsed.size)
        assertTrue(result.storesUsed.containsAll(listOf("Blinkit", "Zepto")))
        assertEquals(127.0, result.grandTotal, 0.01)
        assertEquals(147.0, result.cheapestSingleStoreTotal!!, 0.01)
        assertEquals(20.0, result.absoluteSavings, 0.01)
    }

    @Test
    fun testTwoItemCart_SplitIsMoreExpensive() {
        val item1 = CartItem("1", "Bread", unitQuantity = 400.0, unit = "g", count = 1)
        val item2 = CartItem("2", "Eggs", unitQuantity = 6.0, unit = "pcs", count = 1)
        val cart = listOf(item1, item2)

        val offersMap = mapOf(
            item1 to listOf(
                StoreOffer("Blinkit", "Bread", price = 40.0, unitQuantity = 400.0, unit = "g"),
                StoreOffer("Zepto", "Bread", price = 38.0, unitQuantity = 400.0, unit = "g")
            ),
            item2 to listOf(
                StoreOffer("Blinkit", "Eggs", price = 50.0, unitQuantity = 6.0, unit = "pcs"),
                StoreOffer("Zepto", "Eggs", price = 52.0, unitQuantity = 6.0, unit = "pcs")
            )
        )

        val result = CartOptimizer.optimize(cart, offersMap, policies)

        assertEquals(1, result.storesUsed.size)
        assertEquals("Blinkit", result.storesUsed.first())
        assertEquals(110.0, result.grandTotal, 0.01)
        assertEquals(0.0, result.absoluteSavings, 0.01)
    }

    @Test
    fun testThreeItemCart_ThreeStoresOptimal() {
        val item1 = CartItem("1", "Coffee", unitQuantity = 50.0, unit = "g", count = 1)
        val item2 = CartItem("2", "Tea", unitQuantity = 250.0, unit = "g", count = 1)
        val item3 = CartItem("3", "Sugar", unitQuantity = 1.0, unit = "kg", count = 1)
        val cart = listOf(item1, item2, item3)

        val offersMap = mapOf(
            item1 to listOf(
                StoreOffer("Blinkit", "Coffee", price = 100.0, unitQuantity = 50.0, unit = "g"),
                StoreOffer("Zepto", "Coffee", price = 300.0, unitQuantity = 50.0, unit = "g"),
                StoreOffer("Instamart", "Coffee", price = 300.0, unitQuantity = 50.0, unit = "g")
            ),
            item2 to listOf(
                StoreOffer("Blinkit", "Tea", price = 300.0, unitQuantity = 250.0, unit = "g"),
                StoreOffer("Zepto", "Tea", price = 160.0, unitQuantity = 250.0, unit = "g"),
                StoreOffer("Instamart", "Tea", price = 300.0, unitQuantity = 250.0, unit = "g")
            ),
            item3 to listOf(
                StoreOffer("Blinkit", "Sugar", price = 300.0, unitQuantity = 1.0, unit = "kg"),
                StoreOffer("Zepto", "Sugar", price = 300.0, unitQuantity = 1.0, unit = "kg"),
                StoreOffer("Instamart", "Sugar", price = 260.0, unitQuantity = 1.0, unit = "kg")
            )
        )

        val result = CartOptimizer.optimize(cart, offersMap, policies)

        assertEquals(3, result.storesUsed.size)
        assertEquals(542.0, result.grandTotal, 0.01)
    }

    @Test
    fun testFiveItemCart_FreeDeliveryThresholds() {
        val item1 = CartItem("1", "Atta", unitQuantity = 5.0, unit = "kg", count = 1)
        val item2 = CartItem("2", "Rice", unitQuantity = 1.0, unit = "kg", count = 2)
        val item3 = CartItem("3", "Oil", unitQuantity = 1.0, unit = "L", count = 1)
        val item4 = CartItem("4", "Salt", unitQuantity = 1.0, unit = "kg", count = 1)
        val item5 = CartItem("5", "Spices", unitQuantity = 100.0, unit = "g", count = 1)

        val cart = listOf(item1, item2, item3, item4, item5)

        val offersMap = mapOf(
            item1 to listOf(StoreOffer("Blinkit", "Atta", price = 210.0, unitQuantity = 5.0, unit = "kg")),
            item2 to listOf(StoreOffer("Blinkit", "Rice", price = 60.0, unitQuantity = 1.0, unit = "kg")),
            item3 to listOf(StoreOffer("Blinkit", "Oil", price = 130.0, unitQuantity = 1.0, unit = "L")),
            item4 to listOf(StoreOffer("Blinkit", "Salt", price = 20.0, unitQuantity = 1.0, unit = "kg")),
            item5 to listOf(StoreOffer("Blinkit", "Spices", price = 40.0, unitQuantity = 100.0, unit = "g"))
        )

        val result = CartOptimizer.optimize(cart, offersMap, policies)

        val storeSummary = result.storeBreakdown["Blinkit"]!!
        assertEquals(0.0, storeSummary.deliveryFee, 0.01)
        assertEquals(0.0, storeSummary.amountNeededForFreeDelivery, 0.01)
        assertEquals(525.0, result.grandTotal, 0.01)
    }

    @Test
    fun testUnavailableProduct_SkipsStoreCombination() {
        val item1 = CartItem("1", "Rare Cheese", unitQuantity = 100.0, unit = "g", count = 1)
        val cart = listOf(item1)

        val offersMap = mapOf(
            item1 to listOf(
                StoreOffer("Blinkit", "Rare Cheese", price = 150.0, isAvailable = false, unitQuantity = 100.0, unit = "g"),
                StoreOffer("Zepto", "Rare Cheese", price = 160.0, isAvailable = true, unitQuantity = 100.0, unit = "g")
            )
        )

        val result = CartOptimizer.optimize(cart, offersMap, policies)

        assertEquals(1, result.storesUsed.size)
        assertEquals("Zepto", result.storesUsed.first())
        assertEquals(187.0, result.grandTotal, 0.01)
    }
}