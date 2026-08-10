package net.kiwi.lawncher.billing;

import android.app.Activity;

import net.kiwi.lawncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Billing infra: picks the payment provider, keeps the ownership ledger and
 * purchase history in SharedPreferences, and exposes a clean API for the UI.
 */
public final class BillingManager {

	private static final String PREF_OWNED = "billing.owned";
	private static final String PREF_HISTORY = "billing.history";

	private static BillingManager instance;

	private final BillingProvider provider;
	private final List<IapProduct> catalog = new ArrayList<>();

	private BillingManager() {
		catalog.add(new IapProduct("donation_small", "Coffee for the dev",
				"Support Lawncher's development with a small tip.", 199, "USD", true));
		catalog.add(new IapProduct("donation_medium", "Energy drink",
				"A bigger thank-you that keeps the lights on.", 499, "USD", true));
		catalog.add(new IapProduct("donation_large", "Legendary sponsor",
				"Hero-tier support. Featured in the sidebar for a week.", 999, "USD", true));
		catalog.add(new IapProduct("premium_unlock", "Lawncher Premium",
				"Early access to new launcher features & exclusive wallpapers.", 399, "USD", false));
		provider = PlayBillingProvider.linked() ? new PlayBillingProvider() : new SimulatedBillingProvider();
	}

	public static BillingManager get() {
		if (instance == null) instance = new BillingManager();
		return instance;
	}

	public BillingProvider provider() { return provider; }
	public List<IapProduct> catalog() { return catalog; }

	public IapProduct findProduct(String id) {
		for (IapProduct p : catalog) if (p.id.equals(id)) return p;
		return null;
	}

	public boolean isOwned(String productId) {
		return ownedSet().contains(productId);
	}

	public boolean isPremium() {
		return isOwned("premium_unlock");
	}

	/** Purchases a product; non-consumables are recorded as owned on success. */
	public void purchase(final Activity activity, final String productId, final String title,
			final long priceCents, final String currency, final boolean consumable,
			final BillingProvider.Callback<Boolean> callback) {
		provider.purchase(activity, productId, title, priceCents, currency,
				new BillingProvider.Callback<BillingProvider.PurchaseResult>() {
					@Override public void onResult(boolean success, BillingProvider.PurchaseResult data, String error) {
						if (success) {
							if (!consumable) addOwned(productId);
							addHistory(productId + "|" + (data != null ? data.orderId : "?"));
						}
						if (callback != null) callback.onResult(success, success, error);
					}
				});
	}

	public void consume(String productId) {
		provider.consume(productId, null);
		removeOwned(productId);
	}

	public void restorePurchases(final BillingProvider.Callback<List<String>> callback) {
		provider.restorePurchases(new BillingProvider.Callback<List<String>>() {
			@Override public void onResult(boolean success, List<String> data, String error) {
				if (success && data != null) {
					for (String id : data) addOwned(id);
				}
				if (callback != null) callback.onResult(success, data, error);
			}
		});
	}

	public List<String> purchaseHistory() {
		List<String> out = new ArrayList<>();
		String raw = Prefs.getString(PREF_HISTORY, "");
		if (!raw.isEmpty()) {
			for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	// ---- ledger ----

	private List<String> ownedSet() {
		List<String> out = new ArrayList<>();
		String raw = Prefs.getString(PREF_OWNED, "");
		if (!raw.isEmpty()) {
			for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private void addOwned(String productId) {
		if (isOwned(productId)) return;
		String joined = Prefs.getString(PREF_OWNED, "");
		Prefs.putString(PREF_OWNED, joined.isEmpty() ? productId : joined + "," + productId);
	}

	private void removeOwned(String productId) {
		StringBuilder sb = new StringBuilder();
		for (String id : ownedSet()) {
			if (id.equals(productId)) continue;
			if (sb.length() > 0) sb.append(',');
			sb.append(id);
		}
		Prefs.putString(PREF_OWNED, sb.toString());
	}

	private void addHistory(String entry) {
		String joined = Prefs.getString(PREF_HISTORY, "");
		Prefs.putString(PREF_HISTORY, joined + entry + "\n");
	}
}
