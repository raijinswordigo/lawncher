package net.kiwi.lawncher.billing;

import android.app.Activity;

import java.util.List;

/** Abstraction over a real payment backend (Google Play Billing) or a simulation. */
public interface BillingProvider {

	interface Callback<T> {
		void onResult(boolean success, T data, String error);
	}

	class PurchaseResult {
		public final String orderId;
		public final String productId;
		public final String signature;

		public PurchaseResult(String orderId, String productId, String signature) {
			this.orderId = orderId;
			this.productId = productId;
			this.signature = signature;
		}
	}

	String name();

	boolean isAvailable();

	void queryProducts(List<IapProduct> catalog, Callback<List<IapProduct>> callback);

	void purchase(Activity activity, String productId, String title,
			long priceCents, String currency, Callback<PurchaseResult> callback);

	void consume(String productId, Callback<Boolean> callback);

	void restorePurchases(Callback<List<String>> callback);
}
