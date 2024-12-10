package net.kurasava.smoothnight.config;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    public double modifier = 0.10;
    public static long maxStep = 2400;
    public boolean doSkipWeather = true;
    private static final File configFile = new File("config/smooth-night.json");

    public ModConfig() {
        this.loadConfig();
    }

    private static JSONObject readConfig() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile.getAbsolutePath())) {
                JSONTokener tokener = new JSONTokener(reader);
                return new JSONObject(tokener);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void loadConfig() {
        JSONObject jsonObject = readConfig();
        if (jsonObject != null) {
            this.modifier = jsonObject.getDouble("modifier");
            this.doSkipWeather = jsonObject.getBoolean("skip_weather");
        }
    }

    public void saveConfig() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("modifier", this.modifier);
        jsonObject.put("skip_weather", this.doSkipWeather);

        try (FileWriter file = new FileWriter(configFile)) {
            file.write(jsonObject.toString(4)); // Форматированный вывод в файл
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
