package net.kiwi.lawncher.billing;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulated payment backend for development: ~800ms latency, succeeds unless
 * the product id contains "__fail". Used until Google Play Billing is linked.
 */
public class SimulatedBillingProvider implements BillingProvider {

	private static final Handler UI = new Handler(Looper.getMainLooper());

	@Override public String name() { return "simulated"; }
	@Override public boolean isAvailable() { return true; }

	@Override public void queryProducts(List<IapProduct> catalog, Callback<List<IapProduct>> callback) {
		UI.postDelayed(() -> callback.onResult(true, catalog, null), 400);
	}

	@Override public void purchase(Activity activity, String productId, String title,
			long priceCents, String currency, Callback<PurchaseResult> callback) {
		UI.postDelayed(() -> {
			boolean ok = !productId.contains("__fail");
			if (ok) {
				callback.onResult(true, new PurchaseResult(
						"sim." + System.currentTimeMillis(), productId, "sim-sig"), null);
			} else {
				callback.onResult(false, null, "simulated purchase rejected");
			}
		}, 800);
	}

	@Override public void consume(String productId, Callback<Boolean> callback) {
		UI.postDelayed(() -> {
			if (callback != null) callback.onResult(true, true, null);
		}, 200);
	}

	@Override public void restorePurchases(Callback<List<String>> callback) {
		UI.postDelayed(() -> callback.onResult(true, new ArrayList<String>(), null), 400);
	}
}
