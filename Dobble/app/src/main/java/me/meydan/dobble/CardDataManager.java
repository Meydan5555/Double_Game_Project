package me.meydan.dobble;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CardDataManager {

    // Singleton instance of the manager
    private static CardDataManager instance;

    // Nested map structure to cache card IDs, their corresponding regions, and symbols
    private final Map<String, Map<String, String>> cards = new HashMap<>();

    /**
     * Private constructor to enforce the Singleton pattern.
     * Initializes the data loading process immediately upon creation.
     *
     * @param context The Android context used to access app assets.
     */
    private CardDataManager(Context context) {
        loadCards(context);
    }

    /**
     * Provides synchronized global access to the single instance of CardDataManager.
     * Uses lazy initialization to instantiate the object on the first call.
     *
     * @param context The current context, safely converted to ApplicationContext to avoid leaks.
     * @return The thread-safe unique instance of CardDataManager.
     */
    public static synchronized CardDataManager getInstance(Context context) {
        if (instance == null) {
            // Using application context to prevent memory leaks from short-lived contexts
            instance = new CardDataManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Loads card data from the local 'Cards.json' asset file and parses it into memory.
     * Expects a JSON format mapping Card IDs -> Region Names -> Symbol Values.
     *
     * @param context The context used to open the asset stream.
     * @throws RuntimeException wrapped around any IO or JSON parsing exception.
     */
    private void loadCards(Context context) {
        try {
            // Open an input stream to the JSON file stored in the assets folder
            InputStream inputStream = context.getAssets().open("Cards.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;

            // Read the file line by line and append to the string builder
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();

            // Convert the raw string content into a root JSON object
            JSONObject root = new JSONObject(builder.toString());
            Iterator<String> cardIds = root.keys();

            // Iterate over each card identifier present in the root JSON object
            while (cardIds.hasNext()) {
                String cardId = cardIds.next();
                JSONObject regionsObject = root.getJSONObject(cardId);

                // Create a temporary map to store region-to-symbol relationships for this specific card
                Map<String, String> regions = new HashMap<>();
                Iterator<String> regionNames = regionsObject.keys();

                // Extract all regions and their mapped symbol values from the inner JSON object
                while (regionNames.hasNext()) {
                    String regionName = regionNames.next();
                    regions.put(regionName, regionsObject.getString(regionName));
                }

                // Cache the processed card data into the main class map memory
                cards.put(cardId, regions);
            }
        } catch (Exception error) {
            // Halt application execution if the required data configuration cannot be parsed
            throw new RuntimeException("Could not load Cards.json", error);
        }
    }

    /**
     * Retrieves the specific symbol assigned to a given region on a specific card.
     *
     * @param cardId The identifier of the card to lookup.
     * @param regionName The specific layout region name on the card.
     * @return The symbol value string if found; null if the card or region does not exist.
     */
    public String getSymbol(String cardId, String regionName) {
        // Fetch the regions map for the requested card ID
        Map<String, String> regions = cards.get(cardId);

        // Return null immediately if the requested card ID is invalid or missing
        if (regions == null) {
            return null;
        }

        // Return the symbol string mapped to the specific region name
        return regions.get(regionName);
    }
}
