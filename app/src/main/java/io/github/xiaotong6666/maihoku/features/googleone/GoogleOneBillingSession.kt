package io.github.xiaotong6666.maihoku.features.googleone

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import io.github.xiaotong6666.maihoku.R
import io.github.xiaotong6666.maihoku.hook.ModuleResources
import java.lang.ref.WeakReference
import io.github.xiaotong6666.maihoku.MaiHokuLog as Log

/**
 * One client per foreground Activity. No account switching, payment entry,
 * background purchases, price overrides, or automatic checkout confirmation.
 *
 * The query walks the whole SKU list in bounded batches so the offer inspector
 * covers every Google One / Gemini tier that Play exposes to the account, not
 * only the first few products of a single oversized request.
 */
internal class GoogleOneBillingSession(activity: Activity) {
    private val activityRef = WeakReference(activity)
    private var client: BillingClient? = null

    @Volatile private var requestId = 0

    @Volatile private var closed = false

    private data class AvailableOffer(
        val product: ProductDetails,
        val details: ProductDetails.SubscriptionOfferDetails,
        val phases: List<OfferPhase>,
    ) {
        val isFreeTrial: Boolean get() = OfferPricing.hasFreeFirstPhase(phases)

        val plan: PlanCatalog.Entry? get() = PlanCatalog.find(product.productId, details.offerId)

        fun label(activity: Activity): String {
            val tag = ModuleResources.text(
                activity,
                if (isFreeTrial) R.string.googleone_offer_free else R.string.googleone_offer_paid,
            )
            val entry = plan
            if (entry == null) {
                return ModuleResources.text(
                    activity,
                    R.string.googleone_offer_row,
                    product.productId,
                    details.offerId ?: details.basePlanId,
                    tag,
                )
            }
            val title = if (entry.regionLocked) {
                ModuleResources.text(activity, R.string.googleone_region_tag) + entry.title
            } else {
                entry.title
            }
            return ModuleResources.text(activity, R.string.googleone_plan_row, title, tag)
        }
    }

    fun query() {
        if (closed) return
        val activity = activityRef.get() ?: return
        requestId += 1
        val currentRequest = requestId
        client?.endConnection()
        client = null
        notice(R.string.googleone_querying)
        try {
            val billing = BillingClient.newBuilder(activity.applicationContext)
                .setListener(
                    PurchasesUpdatedListener { result, _ ->
                        if (isCurrent(currentRequest)) {
                            notice(R.string.googleone_purchase_callback, result.responseCode)
                        }
                    },
                )
                .enablePendingPurchases()
                .build()
            client = billing
            billing.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (!isCurrent(currentRequest)) return
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            queryProducts(billing, currentRequest)
                        } else {
                            failure(currentRequest, R.string.googleone_connection_failed, result.responseCode, result.debugMessage)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (isCurrent(currentRequest)) failure(currentRequest, R.string.googleone_connection_lost)
                    }
                },
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Billing client initialization failed", error)
            failure(currentRequest, R.string.googleone_init_failed, error.javaClass.simpleName)
        }
    }

    private fun queryProducts(billing: BillingClient, id: Int) {
        val collected = ArrayList<AvailableOffer>(PRODUCT_IDS.size * 2)
        val failed = ArrayList<String>(PRODUCT_IDS.size)
        var lastCode = 0
        var lastMessage = ""

        fun finish() {
            if (!isCurrent(id)) return
            val offers = collected.sortedWith(
                compareBy<AvailableOffer> { PlanCatalog.orderOf(it.product.productId, it.details.offerId) }
                    .thenByDescending { it.isFreeTrial }
                    .thenBy { it.product.productId }
                    .thenBy { it.details.offerId ?: it.details.basePlanId },
            )
            if (offers.isEmpty() && lastCode != 0) {
                failure(id, R.string.googleone_query_failed, lastCode, lastMessage)
                return
            }
            if (failed.isNotEmpty()) {
                Log.w(TAG, "SKU query failed: ${failed.joinToString()}")
                notice(R.string.googleone_partial_failed, failed.size)
            }
            onActivity(id) { showOffers(it, id, offers) }
        }

        fun querySku(index: Int) {
            if (!isCurrent(id)) return
            if (index >= PRODUCT_IDS.size) {
                finish()
                return
            }
            val sku = PRODUCT_IDS[index]
            val products = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(sku)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
            try {
                billing.queryProductDetailsAsync(
                    params,
                    ProductDetailsResponseListener { result, details ->
                        if (!isCurrent(id)) return@ProductDetailsResponseListener
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                            lastCode = result.responseCode
                            lastMessage = result.debugMessage
                            failed += sku
                            Log.w(TAG, "SKU $sku failed: ${result.responseCode} ${result.debugMessage}")
                        } else {
                            collected += details.orEmpty().flatMap { product ->
                                product.subscriptionOfferDetails.orEmpty().map { offer ->
                                    AvailableOffer(
                                        product = product,
                                        details = offer,
                                        phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                                            OfferPhase(
                                                amountMicros = phase.priceAmountMicros,
                                                formattedPrice = phase.formattedPrice,
                                                billingPeriod = phase.billingPeriod,
                                                billingCycles = phase.billingCycleCount,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        querySku(index + 1)
                    },
                )
            } catch (error: Throwable) {
                Log.e(TAG, "SKU $sku exception", error)
                failed += sku
                lastCode = -1
                lastMessage = error.javaClass.simpleName
                querySku(index + 1)
            }
        }

        querySku(0)
    }

    private fun showOffers(activity: Activity, id: Int, offers: List<AvailableOffer>) {
        if (offers.isEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle(text(activity, R.string.googleone_offers_title))
                .setMessage(text(activity, R.string.googleone_offers_empty))
                .setPositiveButton(text(activity, R.string.googleone_ok), null)
                .show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(text(activity, R.string.googleone_offers_count, offers.size))
            .setItems(offers.map { it.label(activity) }.toTypedArray()) { _, index ->
                if (isCurrent(id)) showOfferDetail(activity, id, offers[index])
            }
            .setNegativeButton(text(activity, R.string.googleone_close), null)
            .show()
    }

    private fun showOfferDetail(activity: Activity, id: Int, offer: AvailableOffer) {
        val message = buildString {
            val entry = offer.plan
            append(
                text(
                    activity,
                    R.string.googleone_plan_line,
                    entry?.title ?: text(activity, R.string.googleone_plan_unknown),
                ),
            )
            if (entry != null) {
                append(
                    text(
                        activity,
                        if (entry.regionLocked) R.string.googleone_region_note else R.string.googleone_region_normal,
                    ),
                )
            }
            append(text(activity, R.string.googleone_product_line, offer.product.productId))
            append(
                text(
                    activity,
                    R.string.googleone_offer_id_line,
                    offer.details.offerId ?: text(activity, R.string.googleone_base_plan),
                ),
            )
            append(text(activity, R.string.googleone_plan_id_line, offer.details.basePlanId))
            append(text(activity, R.string.googleone_pricing_header))
            append(describePhases(activity, offer.phases))
            append(text(activity, R.string.googleone_eligibility_notice))
        }
        val confirmMessage = if (offer.isFreeTrial) {
            R.string.googleone_confirm_message
        } else {
            R.string.googleone_confirm_message_paid
        }
        AlertDialog.Builder(activity)
            .setTitle(text(activity, R.string.googleone_details_title))
            .setMessage(message)
            .setNegativeButton(text(activity, R.string.googleone_back), null)
            .setPositiveButton(text(activity, R.string.googleone_checkout)) { _, _ ->
                AlertDialog.Builder(activity)
                    .setTitle(text(activity, R.string.googleone_confirm_title))
                    .setMessage(text(activity, confirmMessage))
                    .setNegativeButton(text(activity, R.string.googleone_cancel), null)
                    .setPositiveButton(text(activity, R.string.googleone_open_checkout)) { _, _ ->
                        launchOfficialBilling(activity, id, offer)
                    }
                    .show()
            }
            .show()
    }

    private fun launchOfficialBilling(activity: Activity, id: Int, offer: AvailableOffer) {
        if (!isCurrent(id) || activity.isFinishing || activity.isDestroyed) return
        val billing = client ?: return
        try {
            val entry = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(offer.product)
                .setOfferToken(offer.details.offerToken)
                .build()
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(entry))
                .build()
            val result = billing.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                failure(id, R.string.googleone_checkout_failed, result.responseCode, result.debugMessage)
            } else {
                Log.i(TAG, "Official Play checkout launched; user must confirm manually")
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Official checkout launch failed", error)
            failure(id, R.string.googleone_checkout_exception, error.javaClass.simpleName)
        }
    }

    private fun isCurrent(id: Int): Boolean = !closed && id == requestId

    private fun onActivity(id: Int, action: (Activity) -> Unit) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            if (isCurrent(id) && !activity.isFinishing && !activity.isDestroyed) {
                try {
                    action(activity)
                } catch (error: Throwable) {
                    Log.e(TAG, "Google One UI operation failed", error)
                }
            }
        }
    }

    private fun text(activity: Activity, resourceId: Int, vararg arguments: Any): String = ModuleResources.text(activity, resourceId, *arguments)

    private fun describePhases(activity: Activity, phases: List<OfferPhase>): String {
        if (phases.isEmpty()) return text(activity, R.string.googleone_pricing_unavailable)
        return phases.mapIndexed { index, phase ->
            val price = if (phase.amountMicros == 0L) {
                text(activity, R.string.googleone_free_price)
            } else {
                phase.formattedPrice
            }
            val cycleText = if (phase.billingCycles > 0) {
                text(activity, R.string.googleone_phase_cycles, phase.billingCycles)
            } else {
                text(activity, R.string.googleone_phase_ongoing)
            }
            text(activity, R.string.googleone_phase_line, index + 1, price, phase.billingPeriod, cycleText)
        }.joinToString("\n")
    }

    private fun failure(id: Int, resourceId: Int, vararg arguments: Any) {
        onActivity(id) { activity ->
            val message = text(activity, resourceId, *arguments)
            Log.w(TAG, message)
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun notice(resourceId: Int, vararg arguments: Any) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            if (!closed && !activity.isFinishing && !activity.isDestroyed) {
                Toast.makeText(activity, text(activity, resourceId, *arguments), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun close() {
        closed = true
        requestId++
        client?.endConnection()
        client = null
        activityRef.clear()
    }

private companion object {
        const val TAG = "GoogleOneBilling"

        /**
         * Google One / Gemini 订阅 SKU 全集：基础档位 + YouTube 附加项 + 各地区
         * 首期折扣/试用 SKU。Play 只会返回当前账号实际可见的部分，查询侧不再裁剪。
         */
        val PRODUCT_IDS = listOf(
            // 基础档位 (Gói cơ bản)
            "g1.play.20tb",
            "g1.play.ai.pro",
            "g1.30tb",
            "g1.2tb.ai.subs_variant",
            "g1.play.2tb.ai",
            "g1.2tb.subs_variant",
            "g1.2tb.6months_eft",
            "g1.play.helium",

            // 附加项 (Gói đính kèm YouTube)
            "g1.youtube_premium_individual_add_on",
            "g1.youtube_premium_family_add_on",
            "g1.youtube_premium_lite_add_on",

            // Google AI Pro (5 TB) — 用 thử miễn phí & Giảm giá (Ảnh 1)
            "g1.2tb.ai.18months_eft",
            "g1.2tb.ai.15months_eft",
            "g1.2tb.ai.12months_eft",
            "g1.play.2tb.ai.student",
            "g1.2tb.ai.6months_eft",
            "g1.2tb.ai.4months_eft",
            "g1.2tb.ai.3months_eft",
            "g1.2tb.ai.2months_eft",
            "g1.2tb.ai.12months_ip_90p",
            "g1.2tb.ai.12months_ip_75p",
            "g1.2tb.ai.3months_ip_90p.acq",
            "g1.2tb.ai.3months_ip_75p.acq",
            "g1.2tb.ai.2months_ip_90p.acq",
            "g1.2tb.ai.1month_ip_95p.acq",

            // Google AI Ultra (20 TB / 30 TB) — 用 thử miễn phí & Giảm giá (Ảnh 2)
            "g1.20tb.1month_eft.acq",
            "g1.20tb.3months_ip_90p.acq",
            "g1.20tb.3months_ip_80p.acq",
            "g1.20tb.3months_ip_80p",
            "g1.20tb.3months_ip_75p.acq",
            "g1.ai.ultra.1month_eft.acq",
            "g1.ai.ultra.1month_ip_90p.acq",
            "g1.ai.ultra.3months_ip_90p.acq",
            "g1.ai.ultra.3months_ip_80p.acq",
            "g1.ai.ultra.1month_ip_75p.acq",
            "g1.ai.ultra.3months_ip_75p.acq",

            // Google AI Plus (2 TB / 400 GB) & Lưu trữ cơ bản 30GB-200GB (Ảnh 2)
            "g1.2tb.1year_eft",
            "g1.2tb.3months_ip_90p.acq",
            "g1.2tb.3months_ip_85p.acq",
            "g1.play.helium.student",
            "g1.helium.12months_ip_90p",
            "g1.helium.3months_ip_75p.acq",
            "g1.100gb.1year_eft",
            "g1.100gb.annual.1year_eft",
            "g1.100gb.9months_eft",
            "g1.100gb.180days_eft",
            "g1.200gb.1year_eft",
            "g1.200gb.180days_eft",
            "g1.30gb.3month_eft",
            "g1.30gb.1month_eft",

            // Các gói 10TB cũ
            "g1.10tb.1month_ip_90p",
            "g1.10tb.3months_ip_85p",
        )
    }
 }
