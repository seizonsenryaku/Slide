package me.ccrama.redditslide.Fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import me.ccrama.redditslide.Adapters.InboxAdapter;
import me.ccrama.redditslide.Adapters.InboxMessages;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.Reddit;
import me.ccrama.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.ccrama.redditslide.Views.PreCachingLayoutManager;
import me.ccrama.redditslide.Visuals.Palette;
import me.ccrama.redditslide.handler.ToolbarScrollHideHandler;

public class InboxPage extends Fragment {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private InboxAdapter adapter;
    private InboxMessages posts;
    private String id;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        final RecyclerView rv = ((RecyclerView) v.findViewById(R.id.vertical_content));
        final PreCachingLayoutManager mLayoutManager;
        mLayoutManager = new PreCachingLayoutManager(getActivity());
        rv.setLayoutManager(mLayoutManager);

        final SwipeRefreshLayout mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.activity_main_swipe_refresh_layout);
        v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(id, getActivity()));

        //If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        //So, we just do 13% of the phone screen height as a general estimate for the Tabs view type
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int headerOffset = Math.round((float) (screenHeight * 0.13));

        mSwipeRefreshLayout.setProgressViewOffset(false,
                headerOffset - Reddit.pxToDp(42, getContext()),
                headerOffset + Reddit.pxToDp(42, getContext()));

        mSwipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(true);
            }
        });
        posts = new InboxMessages(id);
        adapter = new InboxAdapter(getContext(), posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);

        //TODO catch errors
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(adapter, id, true);

                        //TODO catch errors
                    }
                }
        );
        rv.addOnScrollListener(new ToolbarScrollHideHandler((Toolbar) (getActivity()).findViewById(R.id.toolbar), getActivity().findViewById(R.id.header)) {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                visibleItemCount = rv.getLayoutManager().getChildCount();
                totalItemCount = rv.getLayoutManager().getItemCount();

                if (rv.getLayoutManager() instanceof PreCachingLayoutManager) {
                    pastVisiblesItems = ((PreCachingLayoutManager) rv.getLayoutManager()).findFirstVisibleItemPosition();
                } else {
                    int[] firstVisibleItems = null;
                    firstVisibleItems = ((CatchStaggeredGridLayoutManager) rv.getLayoutManager()).findFirstVisibleItemPositions(firstVisibleItems);

                    if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                        pastVisiblesItems = firstVisibleItems[0];
                    }
                }

                if (!posts.loading && !posts.nomore) {
                    if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                        posts.loading = true;
                        posts.loadMore(adapter, id, false);
                    }
                }
            }
        });
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        id = bundle.getString("id", "");
    }
}