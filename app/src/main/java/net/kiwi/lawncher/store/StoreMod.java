package net.kiwi.lawncher.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A mod listing in the store catalog. */
public class StoreMod {

	public String id;
	public String name;
	public String author;
	public String version;
	public String description;
	public String longDescription;
	public String category = "General";
	public long priceCents;
	public String currency = "USD";
	public String downloadUrl = "";
	public long sizeBytes;
	public int installs;
	public double rating;
	public boolean featured;
	public List<String> screenshots = new ArrayList<>();
	public List<String> tags = new ArrayList<>();

	public boolean isPaid() {
		return priceCents > 0;
	}

	public String priceLabel() {
		if (priceCents <= 0) return "Free";
		String symbol = "$";
		if ("EUR".equalsIgnoreCase(currency)) symbol = "\u20AC";
		else if ("GBP".equalsIgnoreCase(currency)) symbol = "\u00A3";
		else if ("INR".equalsIgnoreCase(currency)) symbol = "\u20B9";
		return symbol + String.format(Locale.US, "%.2f", priceCents / 100.0);
	}

	public String installsLabel() {
		if (installs >= 1_000_000) return (installs / 1_000_000) + "M";
		if (installs >= 1_000) return (installs / 1_000) + "." + ((installs % 1_000) / 100) + "k";
		return String.valueOf(installs);
	}

	public String ratingLabel() {
		return String.format(Locale.US, "%.1f", rating);
	}
}
