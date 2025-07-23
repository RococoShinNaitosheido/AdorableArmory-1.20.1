package flu.kitten.adorablearmory.api;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class RococoDeadGui extends Screen {
    private int delayTicker;
    private final Component causeOfDeath;
    private final boolean hardcore;
    private Component deathScore;
    private final List<Button> exitButtons = Lists.newArrayList();
    @Nullable
    private Button exitToTitleButton;

    public RococoDeadGui(@Nullable Component p_95911_, boolean p_95912_) {
        super(Component.translatable(p_95912_ ? "deathScreen.title.hardcore" : "deathScreen.title"));
        this.causeOfDeath = p_95911_;
        this.hardcore = p_95912_;
    }

    protected void init() {
        this.delayTicker = 0;
        this.exitButtons.clear();
        Component $$0 = this.hardcore ? Component.translatable("deathScreen.spectate") : Component.translatable("deathScreen.respawn");
        this.exitButtons.add((Button)this.addRenderableWidget(Button.builder($$0, (p_280794_) -> {
            if (this.minecraft != null) {
                if (this.minecraft.player != null) {
                    this.minecraft.player.respawn();
                }
            }
            p_280794_.active = false;
        }).bounds(this.width / 2 - 100, this.height / 4 + 72, 200, 20).build()));
        this.exitToTitleButton = (Button)this.addRenderableWidget(Button.builder(Component.translatable("deathScreen.titleScreen"), (p_280796_) -> {
            if (this.minecraft != null) {
                this.minecraft.getReportingContext().draftReportHandled(this.minecraft, this, this::handleExitToTitleScreen, true);
            }
        }).bounds(this.width / 2 - 100, this.height / 4 + 96, 200, 20).build());
        this.exitButtons.add(this.exitToTitleButton);
        this.setButtonsActive(false);
        if (this.minecraft != null) {
            if (this.minecraft.player != null) {
                this.deathScore = Component.translatable("deathScreen.score").append(": ").append(Component.literal(Integer.toString(this.minecraft.player.getScore())).withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void handleExitToTitleScreen() {
        if (this.hardcore) {
            this.exitToTitleScreen();
        } else {
            ConfirmScreen $$0 = new DeathScreen.TitleConfirmScreen((p_280795_) -> {
                if (p_280795_) {
                    this.exitToTitleScreen();
                } else {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.respawn();
                    }
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                }

            }, Component.translatable("deathScreen.quit.confirm"), CommonComponents.EMPTY, Component.translatable("deathScreen.titleScreen"), Component.translatable("deathScreen.respawn"));
            if (this.minecraft != null) {
                this.minecraft.setScreen($$0);
            }
            $$0.setDelay(20);
        }
    }

    private void exitToTitleScreen() {
        if (this.minecraft != null && this.minecraft.level != null) {
            this.minecraft.level.disconnect();
        }

        if (this.minecraft != null) {
            this.minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TitleScreen());
        }
    }

    public void render(GuiGraphics p_283488_, int p_283551_, int p_283002_, float p_281981_) {
        p_283488_.fillGradient(0, 0, this.width, this.height, 1615855616, -1602211792);
        p_283488_.pose().pushPose();
        p_283488_.pose().scale(2.0F, 2.0F, 2.0F);
        p_283488_.drawCenteredString(this.font, this.title, this.width / 2 / 2, 30, 16777215);
        p_283488_.pose().popPose();
        if (this.causeOfDeath != null) {
            p_283488_.drawCenteredString(this.font, this.causeOfDeath, this.width / 2, 85, 16777215);
        }

        p_283488_.drawCenteredString(this.font, this.deathScore, this.width / 2, 100, 16777215);
        if (this.causeOfDeath != null && p_283002_ > 85) {
            Objects.requireNonNull(this.font);
            if (p_283002_ < 85 + 9) {
                Style $$4 = this.getClickedComponentStyleAt(p_283551_);
                p_283488_.renderComponentHoverEffect(this.font, $$4, p_283551_, p_283002_);
            }
        }

        super.render(p_283488_, p_283551_, p_283002_, p_281981_);
        if (this.minecraft != null && this.exitToTitleButton != null && this.minecraft.getReportingContext().hasDraftReport()) {
            p_283488_.blit(AbstractWidget.WIDGETS_LOCATION, this.exitToTitleButton.getX() + this.exitToTitleButton.getWidth() - 17, this.exitToTitleButton.getY() + 3, 182, 24, 15, 15);
        }

    }

    @Nullable
    private Style getClickedComponentStyleAt(int p_95918_) {
        if (this.causeOfDeath == null) {
            return null;
        } else {
            int $$1 = 0;
            if (this.minecraft != null) {
                $$1 = this.minecraft.font.width(this.causeOfDeath);
            }
            int $$2 = this.width / 2 - $$1 / 2;
            int $$3 = this.width / 2 + $$1 / 2;
            return p_95918_ >= $$2 && p_95918_ <= $$3 ? this.minecraft.font.getSplitter().componentStyleAtWidth(this.causeOfDeath, p_95918_ - $$2) : null;
        }
    }

    public boolean mouseClicked(double p_95914_, double p_95915_, int p_95916_) {
        if (this.causeOfDeath != null && p_95915_ > 85.0) {
            Objects.requireNonNull(this.font);
            if (p_95915_ < (double)(85 + 9)) {
                Style $$3 = this.getClickedComponentStyleAt((int)p_95914_);
                if ($$3 != null && $$3.getClickEvent() != null && $$3.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                    this.handleComponentClicked($$3);
                    return false;
                }
            }
        }

        return super.mouseClicked(p_95914_, p_95915_, p_95916_);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void tick() {
        super.tick();
        ++this.delayTicker;
        if (this.delayTicker == 20) {
            this.setButtonsActive(true);
        }

    }

    private void setButtonsActive(boolean p_273413_) {
        Button $$1;
        for(Iterator<Button> var2 = this.exitButtons.iterator(); var2.hasNext(); $$1.active = p_273413_) {
            $$1 = (Button)var2.next();
        }

    }
}
