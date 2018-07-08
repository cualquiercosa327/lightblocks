package de.golfgl.lightblocks.menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.pay.Information;
import com.badlogic.gdx.pay.Offer;
import com.badlogic.gdx.pay.OfferType;
import com.badlogic.gdx.pay.PurchaseManagerConfig;
import com.badlogic.gdx.pay.PurchaseObserver;
import com.badlogic.gdx.pay.Transaction;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

import de.golfgl.gdx.controllers.ControllerMenuDialog;
import de.golfgl.lightblocks.LightBlocksGame;
import de.golfgl.lightblocks.scene2d.InfoButton;
import de.golfgl.lightblocks.scene2d.ProgressDialog;
import de.golfgl.lightblocks.scene2d.RoundedTextButton;
import de.golfgl.lightblocks.scene2d.ScaledLabel;
import de.golfgl.lightblocks.scene2d.ScoreLabel;
import de.golfgl.lightblocks.scene2d.VetoDialog;

/**
 * Created by Benjamin Schulte on 24.06.2018.
 */

public class DonationDialog extends ControllerMenuDialog {
    private static final String LIGHTBLOCKS_SUPPORTER = "lightblocks.supporter";
    private static final String LIGHTBLOCKS_SPONSOR = "lightblocks.sponsor";
    private static final String LIGHTBLOCKS_PATRON = "lightblocks.patron";
    private final LightBlocksGame app;
    private final RoundedTextButton reclaimButton;
    private final RoundedTextButton closeButton;
    private Cell mainDonationButtonsCell;
    private DonationButton donateSupporter;
    private Table donationButtonTable;
    private DonationButton donateSponsor;
    private DonationButton donatePatron;
    private ScaledLabel doDonateLabel;

    public DonationDialog(final LightBlocksGame app) {
        super("", app.skin);
        this.app = app;

        // TODO: im aggressiven Modus frühestens 5 Sekunden nach Start aktivieren
        closeButton = new RoundedTextButton(app.TEXTS.get("donationNoThanks"), app.skin);
        button(closeButton);

        reclaimButton = new RoundedTextButton(app.TEXTS.get("donationReclaim"), app.skin);
        reclaimButton.setDisabled(true);
        reclaimButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                reclaimButton.setDisabled(true);
                app.purchaseManager.purchaseRestore();
            }
        });
        addFocusableActor(reclaimButton);
        getButtonTable().add(reclaimButton);

        // GUI erstmal so aufbauen
        fillContent(app);

        // den Init lostreten so früh es geht, aber nicht bevor die GUI-Referenzen existieren :-)
        initPurchaseManager();
    }

    private void fillContent(LightBlocksGame app) {
        Table contentTable = getContentTable();
        contentTable.pad(10);

        long drawnTetrominos = app.savegame.getTotalScore().getDrawnTetrominos();
        if (drawnTetrominos > 5000) {
            contentTable.add(new ScaledLabel(app.TEXTS.get("donationYouStacked1"), app.skin,
                    LightBlocksGame.SKIN_FONT_TITLE));
            ScoreLabel scoreLabel = new ScoreLabel(0, 0, app.skin, LightBlocksGame.SKIN_FONT_TITLE);
            scoreLabel.setMaxCountingTime(2);
            scoreLabel.setCountingSpeed(2000);
            scoreLabel.setScore(drawnTetrominos);
            contentTable.row();
            contentTable.add(scoreLabel).height(scoreLabel.getPrefHeight() * .8f);
            contentTable.row();
            contentTable.add(new ScaledLabel(app.TEXTS.get("donationYouStacked2"), app.skin,
                    LightBlocksGame.SKIN_FONT_TITLE));
            contentTable.row().padTop(10);
        }

        ScaledLabel textLabel = new ScaledLabel(app.TEXTS.get("donationText"), app.skin);
        textLabel.setWrap(true);
        textLabel.setAlignment(Align.center);
        contentTable.add(textLabel).fillX().minWidth(LightBlocksGame.nativeGameWidth * .8f);
        contentTable.row().padTop(20);
        doDonateLabel = new ScaledLabel(app.TEXTS.get("donationIntro"), app.skin, LightBlocksGame.SKIN_FONT_TITLE);
        doDonateLabel.setVisible(false);
        contentTable.add(doDonateLabel);
        contentTable.row();
        // erstmal schicke Animation bis alles soweit ist...
        mainDonationButtonsCell = contentTable.add(new ProgressDialog.WaitRotationImage(app));

        donationButtonTable = new Table();
        donationButtonTable.defaults().fillX().uniform().expandX();
        donateSupporter = new DonationButton(LIGHTBLOCKS_SUPPORTER);
        donationButtonTable.add(donateSupporter);
        donateSponsor = new DonationButton(LIGHTBLOCKS_SPONSOR);
        donationButtonTable.add(donateSponsor);
        donationButtonTable.row();
        donatePatron = new DonationButton(LIGHTBLOCKS_PATRON);
        donationButtonTable.add(donatePatron);
        ScaledLabel hintLabel = new ScaledLabel(app.TEXTS.get("donationHelp"), app.skin);
        hintLabel.setWrap(true);
        hintLabel.setAlignment(Align.center);
        donationButtonTable.add(hintLabel).fill();

        mainDonationButtonsCell.minSize(donationButtonTable.getPrefWidth(), donationButtonTable.getPrefHeight());
    }

    private void initPurchaseManager() {
        // IAP
        PurchaseManagerConfig pmc = new PurchaseManagerConfig();
        pmc.addOffer(new Offer().setType(OfferType.ENTITLEMENT).setIdentifier(LIGHTBLOCKS_SUPPORTER));
        pmc.addOffer(new Offer().setType(OfferType.ENTITLEMENT).setIdentifier(LIGHTBLOCKS_SPONSOR));
        pmc.addOffer(new Offer().setType(OfferType.ENTITLEMENT).setIdentifier(LIGHTBLOCKS_PATRON));

        app.purchaseManager.install(new LbPurchaseObserver(), pmc, true);
    }

    private void updateGuiWhenPurchaseManInstalled(String errorMessage) {
        if (app.purchaseManager.installed() && errorMessage == null) {
            // einfüllen der Infos
            donateSupporter.updateFromManager();
            donateSponsor.updateFromManager();
            donatePatron.updateFromManager();

            reclaimButton.setDisabled(false);
            doDonateLabel.setVisible(true);
            mainDonationButtonsCell.setActor(donationButtonTable);
            mainDonationButtonsCell.fillX();
        } else {
            errorMessage = "Error instantiating the donation system:"
                    + (errorMessage == null ? "" : "\n" + errorMessage);
            ScaledLabel errorLabel = new ScaledLabel(errorMessage, app.skin,
                    LightBlocksGame.SKIN_FONT_BIG);
            errorLabel.setWrap(true);
            errorLabel.setAlignment(Align.center);
            mainDonationButtonsCell.setActor(errorLabel);
            mainDonationButtonsCell.fillX();
        }
    }

    @Override
    protected Actor getConfiguredEscapeActor() {
        return closeButton;
    }

    private class DonationButton extends InfoButton {
        private final String sku;

        public DonationButton(String sku) {
            // feste Werte damit die Breite und Höhe schonmal passt
            super(app.TEXTS.get("donationType_" + sku), "x,xxx", app.skin);
            this.sku = sku;
            addFocusableActor(this);
            getDescLabel().setAlignment(Align.center);

            addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    buyItem();
                }
            });
        }

        private void buyItem() {
            app.purchaseManager.purchase(sku);
        }

        public void setBought() {
            setDisabled(true);
            getDescLabel().setText(app.TEXTS.get("donationThankYou"));
            closeButton.setText(app.TEXTS.get("donationButtonClose"));
        }

        public void updateFromManager() {
            Information skuInfo = app.purchaseManager.getInformation(sku);

            if (skuInfo == null || skuInfo.equals(Information.UNAVAILABLE)) {
                setDisabled(true);
                getDescLabel().setText("Not available");
            } else {
                getDescLabel().setText(skuInfo.getLocalPricing());
            }
        }
    }

    private class LbPurchaseObserver implements PurchaseObserver {

        @Override
        public void handleInstall() {
            Gdx.app.log("LB-IAP", "Installed");

            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    updateGuiWhenPurchaseManInstalled(null);
                }
            });
        }

        @Override
        public void handleInstallError(final Throwable e) {
            Gdx.app.error("LB-IAP", "Error when trying to install PurchaseManager", e);
            //TODO GA-Meldung

            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    updateGuiWhenPurchaseManInstalled(e.getMessage());
                }
            });
        }

        @Override
        public void handleRestore(final Transaction[] transactions) {
            for (Transaction t : transactions) {
                handlePurchase(t);
            }
        }

        @Override
        public void handleRestoreError(Throwable e) {
            showErrorOnMainThread("Error reclaiming donations: " + e.getMessage());
        }

        @Override
        public void handlePurchase(final Transaction transaction) {
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (transaction.isPurchased()) {
                        //TODO GA-Meldung (aber nicht bei reclaim)
                        // TODO richtig persistieren
                        if (transaction.getIdentifier().equals(LIGHTBLOCKS_SUPPORTER))
                            donateSupporter.setBought();
                        else if (transaction.getIdentifier().equals(LIGHTBLOCKS_SPONSOR))
                            donateSponsor.setBought();
                        else if (transaction.getIdentifier().equals(LIGHTBLOCKS_PATRON))
                            donatePatron.setBought();

                    }
                }
            });
        }

        @Override
        public void handlePurchaseError(Throwable e) {
            showErrorOnMainThread("Error making donation:\n" + e.getMessage());
        }

        @Override
        public void handlePurchaseCanceled() {

        }

        private void showErrorOnMainThread(final String message) {
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    new VetoDialog(message, getSkin(), getWidth()).show(getStage());
                }
            });
        }
    }
}
