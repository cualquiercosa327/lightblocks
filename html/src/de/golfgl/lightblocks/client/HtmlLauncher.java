package de.golfgl.lightblocks.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.badlogic.gdx.backends.gwt.preloader.Preloader;
import com.badlogic.gdx.utils.TimeUtils;
import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.CssColor;

import de.golfgl.lightblocks.LightBlocksGame;

public class HtmlLauncher extends GwtApplication {

    long loadStart = TimeUtils.nanoTime();

    @Override
    public GwtApplicationConfiguration getConfig() {
        return new GwtApplicationConfiguration(LightBlocksGame.nativeGameWidth, LightBlocksGame.nativeGameHeight);
    }

    @Override
    public ApplicationListener createApplicationListener() {
        return new LightBlocksGame();
    }

    @Override
    public Preloader.PreloaderCallback getPreloaderCallback() {
        final Canvas canvas = Canvas.createIfSupported();
        canvas.setWidth("" + (int) (LightBlocksGame.nativeGameWidth * 0.7f) + "px");
        canvas.setHeight("70px");
        getRootPanel().add(canvas);
        final Context2d context = canvas.getContext2d();
        context.setTextAlign(Context2d.TextAlign.CENTER);
        context.setTextBaseline(Context2d.TextBaseline.MIDDLE);
        context.setFont("18pt Calibri");

        return new Preloader.PreloaderCallback() {
            @Override
            public void update(Preloader.PreloaderState state) {
                if (state.hasEnded()) {
                    context.fillRect(0, 0, 300, 40);
                } else {
                    System.out.println("loaded " + state.getProgress());
                    CssColor color = CssColor.make(30, 30, 30);
                    context.setFillStyle(color);
                    context.setStrokeStyle(color);
                    context.fillRect(0, 0, 300, 70);
                    color = CssColor.make(200, 200, 200); //, (((TimeUtils.nanoTime() - loadStart) % 1000000000) /
                    // 1000000000f));
                    context.setFillStyle(color);
                    context.setStrokeStyle(color);
                    context.fillRect(0, 0, 300 * (state.getDownloadedSize() / (float) state.getTotalSize()) * 0.97f,
                            70);

                    context.setFillStyle(CssColor.make(50, 50, 50));
                    context.fillText("loading", 300 / 2, 70 / 2);

                }
            }

            @Override
            public void error(String file) {
                System.out.println("error: " + file);
            }
        };
    }
}