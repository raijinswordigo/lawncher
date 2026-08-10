package net.kiwi.lawncher.billing;

import android.app.Activity;

import java.util.List;

/**
 * Google Play Billing integration point.
 *
 * To go live:
 *   1. add `com.android.billingclient:billing` to app/build.gradle
 *   2. implement queryProducts()/purchase() with BillingClient.newBuilder(...),
 *      BillingFlowParams, PurchasesUpdatedListener, etc.
 *   3. verify the signature in PurchaseResult before granting entitlement.
 *
 * Until the library is linked, {@link #linked()} returns false and
 * BillingManager falls back to SimulatedBillingProvider.
 */
public class PlayBillingProvider implements BillingProvider {

	public static boolean linked() {
		try {
			Class.forName("com.android.billingclient.api.BillingClient");
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override public String name() { return "google_play"; }
	@Override public boolean isAvailable() { return linked(); }

	@Override public void queryProducts(List<IapProduct> catalog, Callback<List<IapProduct>> callback) {
		callback.onResult(false, null, "play billing not linked");
	}

	@Override public void purchase(Activity activity, String productId, String title,
			long priceCents, String currency, Callback<PurchaseResult> callback) {
		callback.onResult(false, null, "play billing not linked");
	}

	@Override public void consume(String productId, Callback<Boolean> callback) {
		callback.onResult(false, null, "play billing not linked");
	}

	@Override public void restorePurchases(Callback<List<String>> callback) {
		callback.onResult(false, null, "play billing not linked");
	}
}
