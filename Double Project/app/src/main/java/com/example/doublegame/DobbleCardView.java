package com.example.doublegame;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class DobbleCardView extends FrameLayout {

    public interface OnSymbolClickListener {
        void onSymbolClicked(int symbolId);
    }

    private static final SymbolLocation[] SLOTS = new SymbolLocation[]{
            new SymbolLocation(0.50f, 0.18f, 0.18f, -8f),
            new SymbolLocation(0.28f, 0.26f, 0.16f, 15f),
            new SymbolLocation(0.72f, 0.26f, 0.16f, -16f),
            new SymbolLocation(0.20f, 0.50f, 0.16f, 10f),
            new SymbolLocation(0.50f, 0.50f, 0.20f, -5f),
            new SymbolLocation(0.80f, 0.50f, 0.16f, 18f),
            new SymbolLocation(0.32f, 0.74f, 0.16f, -12f),
            new SymbolLocation(0.68f, 0.74f, 0.16f, 8f)
    };

    private LogicalCard logicalCard;
    private boolean clickableSymbols = false;
    private OnSymbolClickListener listener;

    public DobbleCardView(Context context) {
        super(context);
        init();
    }

    public DobbleCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DobbleCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
        setBackground(createCardBackground());
        setPadding(8, 8, 8, 8);
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xFFFFFFFF);
        drawable.setStroke(3, 0xFF333333);
        return drawable;
    }

    public void setLogicalCard(LogicalCard logicalCard) {
        this.logicalCard = logicalCard;
        post(this::renderCard);
    }

    public void setSymbolsClickable(boolean clickable) {
        this.clickableSymbols = clickable;
        if (logicalCard != null) {
            post(this::renderCard);
        }
    }

    public void setOnSymbolClickListener(OnSymbolClickListener listener) {
        this.listener = listener;
    }

    private void renderCard() {
        removeAllViews();

        if (logicalCard == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        int cardSize = Math.min(getWidth(), getHeight());

        for (int i = 0; i < 8; i++) {
            int symbolId = logicalCard.getSymbolAt(i);
            SymbolLocation slot = SLOTS[i];

            ImageView imageView = new ImageView(getContext());
            imageView.setImageResource(ImageUtils.getImageResId(symbolId));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setTag(symbolId);

            int size = (int) (cardSize * slot.sizePercent);
            int left = (int) (getWidth() * slot.xPercent - size / 2f);
            int top = (int) (getHeight() * slot.yPercent - size / 2f);

            LayoutParams params = new LayoutParams(size, size);
            params.leftMargin = left;
            params.topMargin = top;
            imageView.setLayoutParams(params);

            imageView.setRotation(slot.rotation);

            if (clickableSymbols) {
                imageView.setOnClickListener(v -> {
                    if (listener != null) {
                        int clickedSymbolId = (int) v.getTag();
                        listener.onSymbolClicked(clickedSymbolId);
                    }
                });
            }

            addView(imageView);
        }
    }
}