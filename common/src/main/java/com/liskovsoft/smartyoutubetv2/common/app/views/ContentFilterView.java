package com.liskovsoft.smartyoutubetv2.common.app.views;

/**
 * BlockTube: full screen "Content filter" (menu on the left, list on the right).
 */
public interface ContentFilterView {
    /**
     * Reload the menu and the current section, keeping the focus position.
     */
    void update();

    void selectSection(int section);
}
