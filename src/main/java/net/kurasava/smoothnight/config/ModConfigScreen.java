package net.kurasava.smoothnight.config;

import net.kurasava.smoothnight.SmoothNight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

public class ModConfigScreen extends GameOptionsScreen {

    SmoothNight INSTANCE = SmoothNight.INSTANCE;

    public ModConfigScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.of("Smooth Night Options"));
    }

    @Override
    protected void addOptions() {
        if (this.body != null) {
            this.body.addAll(generateToggle(), generateSlider());
        }
    }

    private SimpleOption<Double> generateSlider() {
        return new SimpleOption<>(
                "Modifier",
                SimpleOption.constantTooltip(Text.of("Adjusts the speed of night skipping, where 0.00 is very slow and 1.00 is instant.")),
                (optionText, value) -> Text.literal(String.format("Speed Modifier: %.2f", value)),
                SimpleOption.DoubleSliderCallbacks.INSTANCE,
                this.INSTANCE.config.modifier,
                value -> this.INSTANCE.config.modifier = value
        );
    }

    private SimpleOption<Boolean> generateToggle() {
        return new SimpleOption<>(
                "Skip Weather",
                SimpleOption.constantTooltip(Text.of("If enabled, the weather will be skipped at the beginning of a new day, as in vanilla. \nThunderstorms are always skipped!")),
                (optionText, value) -> {
                    TextColor color = value ? TextColor.fromRgb(0x00FF00) : TextColor.fromRgb(0xFF0000);
                    return Text.literal(value ? "Enabled" : "Disabled").setStyle(Style.EMPTY.withColor(color));
                },
                SimpleOption.BOOLEAN,
                this.INSTANCE.config.doSkipWeather,
                value -> this.INSTANCE.config.doSkipWeather = value
        );
    }

    @Override
    public void removed() {
        super.removed();
        this.INSTANCE.config.saveConfig();
    }
}


