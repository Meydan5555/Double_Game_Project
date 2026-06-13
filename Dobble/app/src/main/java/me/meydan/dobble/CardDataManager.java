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

    private static CardDataManager instance;

    private final Map<String, Map<String, String>> cards =
            new HashMap<>();

    private CardDataManager(Context context) {
        loadCards(context);
    }

    public static synchronized CardDataManager getInstance(
            Context context
    ) {
        if (instance == null) {
            instance = new CardDataManager(
                    context.getApplicationContext()
            );
        }

        return instance;
    }

    private void loadCards(Context context) {
        try {
            InputStream inputStream =
                    context.getAssets().open("Cards.json");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream)
            );

            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            reader.close();

            JSONObject root =
                    new JSONObject(builder.toString());

            Iterator<String> cardIds = root.keys();

            while (cardIds.hasNext()) {
                String cardId = cardIds.next();

                JSONObject regionsObject =
                        root.getJSONObject(cardId);

                Map<String, String> regions =
                        new HashMap<>();

                Iterator<String> regionNames =
                        regionsObject.keys();

                while (regionNames.hasNext()) {
                    String regionName =
                            regionNames.next();

                    regions.put(
                            regionName,
                            regionsObject.getString(regionName)
                    );
                }

                cards.put(cardId, regions);
            }

        } catch (Exception error) {
            throw new RuntimeException(
                    "Could not load Cards.json",
                    error
            );
        }
    }

    public String getSymbol(
            String cardId,
            String regionName
    ) {
        Map<String, String> regions =
                cards.get(cardId);

        if (regions == null) {
            return null;
        }

        return regions.get(regionName);
    }
}