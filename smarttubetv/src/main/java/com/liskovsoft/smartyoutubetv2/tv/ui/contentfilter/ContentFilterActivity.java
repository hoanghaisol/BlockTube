package com.liskovsoft.smartyoutubetv2.tv.ui.contentfilter;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.ContentFilterPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ContentFilterPresenter.Item;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ContentFilterPresenter.MenuEntry;
import com.liskovsoft.smartyoutubetv2.common.app.views.ContentFilterView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockTube: full screen Content filter. Focusing a menu entry shows its list on the right;
 * actions keep the screen open so many channels can be unblocked in a row.
 */
public class ContentFilterActivity extends LeanbackActivity implements ContentFilterView {
    private static final int[] INITIAL_COLORS = {0xFF5C6BC0, 0xFF26A69A, 0xFFEF5350, 0xFFAB47BC, 0xFFFFA726, 0xFF42A5F5, 0xFF8D6E63};
    private ContentFilterPresenter mPresenter;
    private RecyclerView mMenuView;
    private RecyclerView mListView;
    private TextView mHintView;
    private TextView mEmptyView;
    private final MenuAdapter mMenuAdapter = new MenuAdapter();
    private final ItemAdapter mItemAdapter = new ItemAdapter();
    private int mSection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_filter);

        ((TextView) findViewById(R.id.content_filter_title)).setText(com.liskovsoft.smartyoutubetv2.common.R.string.content_filter_title);
        mMenuView = findViewById(R.id.content_filter_menu);
        mListView = findViewById(R.id.content_filter_list);
        mHintView = findViewById(R.id.content_filter_hint);
        mEmptyView = findViewById(R.id.content_filter_empty);

        mMenuView.setLayoutManager(new LinearLayoutManager(this));
        mMenuView.setAdapter(mMenuAdapter);
        mListView.setLayoutManager(new LinearLayoutManager(this));
        mListView.setAdapter(mItemAdapter);
        mListView.setItemAnimator(null); // keep the focus steady when a row is removed

        mPresenter = ContentFilterPresenter.instance(this);
        mPresenter.setView(this);
        mSection = mPresenter.getStartSection();
        update();
        mMenuView.post(() -> focusMenu(mSection));
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void finish() {
        super.finish();
        finishReally();
    }

    @Override
    public void update() {
        // Menu titles carry counts; rebinding it is safe only when the focus isn't there
        boolean menuFocused = mMenuView.getFocusedChild() != null;
        List<MenuEntry> menu = mPresenter.getMenu();
        boolean found = false;
        for (MenuEntry entry : menu) {
            found |= entry.section == mSection;
        }
        if (!found && !menu.isEmpty()) {
            mSection = menu.get(0).section;
        }
        mMenuAdapter.setEntries(menu);
        showSection(mSection);
        if (menuFocused) {
            focusMenu(mSection);
        }
    }

    @Override
    public void selectSection(int section) {
        mSection = section;
        update();
        focusMenu(section);
    }

    private void showSection(int section) {
        mSection = section;

        // Keep the focus on the same row index after a row was removed
        View focused = mListView.getFocusedChild();
        int focusedPosition = focused != null ? mListView.getChildAdapterPosition(focused) : -1;

        List<Item> items = mPresenter.getItems(section);
        mItemAdapter.setItems(items);
        mMenuAdapter.setSelected(section);

        String hint = mPresenter.getHint(section);
        mHintView.setText(hint);
        mHintView.setVisibility(hint != null ? View.VISIBLE : View.GONE);
        mEmptyView.setText(com.liskovsoft.smartyoutubetv2.common.R.string.content_filter_empty);
        mEmptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        if (focusedPosition != -1) {
            if (items.isEmpty()) {
                focusMenu(section);
            } else {
                int position = Math.min(focusedPosition, items.size() - 1);
                mListView.scrollToPosition(position);
                mListView.post(() -> {
                    RecyclerView.ViewHolder holder = mListView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        holder.itemView.requestFocus();
                    }
                });
            }
        }
    }

    private void focusMenu(int section) {
        int position = mMenuAdapter.positionOf(section);
        if (position == -1) {
            return;
        }
        mMenuView.scrollToPosition(position);
        mMenuView.post(() -> {
            RecyclerView.ViewHolder holder = mMenuView.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                holder.itemView.requestFocus();
            }
        });
    }

    private void focusFirstItem() {
        if (mItemAdapter.getItemCount() == 0) {
            return;
        }
        RecyclerView.ViewHolder holder = mListView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            holder.itemView.requestFocus();
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }
    }

    private class MenuAdapter extends RecyclerView.Adapter<Holder> {
        private final List<MenuEntry> mEntries = new ArrayList<>();
        private int mSelected = -1;

        void setEntries(List<MenuEntry> entries) {
            mEntries.clear();
            mEntries.addAll(entries);
            notifyDataSetChanged();
        }

        /**
         * No rebind here: rebinding the menu while it has focus moves the focus to the first entry.
         */
        void setSelected(int section) {
            mSelected = section;
            for (int i = 0; i < mMenuView.getChildCount(); i++) {
                View child = mMenuView.getChildAt(i);
                int position = mMenuView.getChildAdapterPosition(child);
                if (position != RecyclerView.NO_POSITION) {
                    child.setActivated(mEntries.get(position).section == section);
                }
            }
        }

        int positionOf(int section) {
            for (int i = 0; i < mEntries.size(); i++) {
                if (mEntries.get(i).section == section) {
                    return i;
                }
            }
            return -1;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.content_filter_menu_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MenuEntry entry = mEntries.get(position);
            View view = holder.itemView;

            ((TextView) view.findViewById(R.id.content_filter_menu_title)).setText(entry.title);
            view.findViewById(R.id.content_filter_menu_dot).setVisibility(entry.hasUnread ? View.VISIBLE : View.INVISIBLE);
            view.setActivated(entry.section == mSelected);
            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && entry.section != mSection) {
                    showSection(entry.section);
                }
            });
            view.setOnClickListener(v -> focusFirstItem());
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }
    }

    private class ItemAdapter extends RecyclerView.Adapter<Holder> {
        private final List<Item> mItems = new ArrayList<>();

        void setItems(List<Item> items) {
            mItems.clear();
            mItems.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.content_filter_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Item item = mItems.get(position);
            View view = holder.itemView;

            view.findViewById(R.id.content_filter_row_dot).setVisibility(item.unread ? View.VISIBLE : View.INVISIBLE);

            TextView initial = view.findViewById(R.id.content_filter_row_initial);
            String title = item.title != null ? item.title : "";
            initial.setText(title.isEmpty() ? "" : title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase());
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(INITIAL_COLORS[Math.abs(title.hashCode()) % INITIAL_COLORS.length]);
            initial.setBackground(circle);
            initial.setVisibility(item.isChannel ? View.VISIBLE : View.GONE);

            ((TextView) view.findViewById(R.id.content_filter_row_title)).setText(title);
            setText(view.findViewById(R.id.content_filter_row_subtitle), item.subtitle);
            setText(view.findViewById(R.id.content_filter_row_action), item.action);

            view.setOnClickListener(v -> item.onClick.run());
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        private void setText(TextView view, String text) {
            view.setText(text);
            view.setVisibility(text != null ? View.VISIBLE : View.GONE);
        }
    }
}
