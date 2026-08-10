package net.kiwi.lawncher.billing;

import java.util.Locale;

/** A purchasable product definition (donations, premium, store mods). */
public class IapProduct {

	public final String id;
	public final String title;
	public final String description;
	public final long priceCents;
	public final String currency;
	public final boolean consumable;

	public IapProduct(String id, String title, String description,
			long priceCents, String currency, boolean consumable) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.priceCents = priceCents;
		this.currency = currency;
		this.consumable = consumable;
	}

	public String priceLabel() {
		String symbol = "$";
		if ("EUR".equalsIgnoreCase(currency)) symbol = "\u20AC";
		else if ("GBP".equalsIgnoreCase(currency)) symbol = "\u00A3";
		else if ("INR".equalsIgnoreCase(currency)) symbol = "\u20B9";
		return symbol + String.format(Locale.US, "%.2f", priceCents / 100.0);
	}
}
