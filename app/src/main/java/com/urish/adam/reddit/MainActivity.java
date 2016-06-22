package com.urish.adam.reddit;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;


import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthException;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.SubredditPaginator;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, SubredditPickerDialog.DialogListener{
    RedditClient redditClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        RedditManager redditManager = new RedditManager(this);
        redditManager.execute();
    }
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if(id == R.id.subredditButton)
        {
            SubredditPickerDialog subredditPickerDialog = new SubredditPickerDialog();
            subredditPickerDialog.show(getFragmentManager(),"subredditPicker");
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
    private void launchCustomTab(String url){
        CustomTabsIntent.Builder customTabBuilder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = customTabBuilder.build();
        customTabsIntent.launchUrl(this,Uri.parse(url));
    }
    private void setRedditClient(RedditClient redditClient){
        this.redditClient = redditClient;
        startSubredditUpdate(20,"flind");
    }
    private void startSubredditUpdate(int amount, String subreddit){
        SubredditGetter subredditGetter = new SubredditGetter(this);
        subredditGetter.setAmountToQuery(amount);
        subredditGetter.setSubToQuery(subreddit);
        subredditGetter.execute(this.redditClient);
        setTitle(subreddit);
    }
    private void populateListView(Listing<Submission> submissions){
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);
        ArrayList<String> titles = new ArrayList<>();
        final Listing<Submission> finalSubmissions = submissions;
        for(Submission submission : submissions)
        {
            String titleBuilder = String.valueOf(submission.getScore()) +
                    " -- " +
                    submission.getTitle();
            titles.add(titleBuilder);
        }
        ListView listView = (ListView) findViewById(R.id.realTextField);
        ArrayAdapter<String> submissionArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles.toArray(new String[titles.size()]));
        AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                System.out.println("clicked");
                launchCustomTab(finalSubmissions.get(i).getUrl());
            }
        };
        listView.setAdapter(submissionArrayAdapter);
        listView.setOnItemClickListener(itemClickListener);
        ObjectAnimator listViewAnimator = ObjectAnimator.ofFloat(listView,"alpha",0f,1f);
        listViewAnimator.setDuration(250);
        listViewAnimator.start();
        if(submissions.size() < 2){
            Log.i("ListView","There are few posts, probably invalid subreddit");
            Snackbar.make(this.findViewById(getTaskId()),"There is nothing here",Snackbar.LENGTH_INDEFINITE);
        }
    }

    @Override
    public void onPosClick(DialogFragment dialogFragment) {
        ListView listView = (ListView) findViewById(R.id.realTextField);
        ObjectAnimator listViewAnimator = ObjectAnimator.ofFloat(listView,"alpha",1f,0f);
        listViewAnimator.setDuration(250);
        listViewAnimator.start();
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);
        startSubredditUpdate(20,((EditText)dialogFragment.getDialog().findViewById(R.id.subredditPicker)).getText().toString());

    }

    private class SubredditGetter extends AsyncTask<RedditClient,Void,Listing<Submission>> {
        private int amountToQuery = 5;
        private String subToQuery;
        private Activity parentActivity;
        public void setAmountToQuery(int amountToQuery) {
            this.amountToQuery = amountToQuery;
        }
        public void setSubToQuery(String subToQuery) {
            this.subToQuery = subToQuery;
        }
        public SubredditGetter(Activity parentActivity){
            this.parentActivity = parentActivity;
        }
        @Override
        protected Listing<Submission> doInBackground(RedditClient... redditClients) {
            RedditClient redditClient = redditClients[0];
            SubredditPaginator subredditPaginator = new SubredditPaginator(redditClient);
            if(subToQuery != null){
                Log.i("SubredditGetter","Going to "+subToQuery);
                subredditPaginator.setSubreddit(subToQuery);
            }
            subredditPaginator.setLimit(amountToQuery);
            Listing<Submission> submissions;
            if(subredditPaginator.hasNext()) {
                submissions = subredditPaginator.next();
            }
            else {
                Log.e("SubredditGetter", "Invalid subreddit");
                Snackbar.make(this.parentActivity.findViewById(R.id.drawer_layout), "Invalid subreddit", Snackbar.LENGTH_LONG).show();
                submissions = new Listing<Submission>(null);
            }
            return submissions;
        }

        @Override
        protected void onPostExecute(Listing<Submission> submissions) {
            super.onPostExecute(submissions);
            populateListView(submissions);
        }
    }
    private class RedditManager extends AsyncTask<Void,Void,RedditClient>{
        private Activity parentActivity;
        public RedditManager(Activity parentActivity){
            this.parentActivity = parentActivity;
        }
        @Override
        protected RedditClient doInBackground(Void... voids) {
            UUID deviceUUID = UUID.randomUUID();
            UserAgent userAgent = UserAgent.of("android","com.urish.adam.reddit","v0.1","ack6600");
            System.out.println("generated useragent");
            RedditClient redditClient = new RedditClient(userAgent);
            System.out.println("generated redditclient");
            Credentials credentials = Credentials.userlessApp("R0Tl78hCI4Pq7A",deviceUUID);
            System.out.println("generated credentials");
            OAuthData oAuthData = null;
            try {
                oAuthData = redditClient.getOAuthHelper().easyAuth(credentials);
            } catch (NetworkException e) {
                e.printStackTrace();
            } catch (OAuthException e) {
                e.printStackTrace();
            }
            redditClient.authenticate(oAuthData);
            System.out.println("successfully authenticated");
            return redditClient;
        }

        @Override
        protected void onPostExecute(RedditClient redditClient) {
            super.onPostExecute(redditClient);
            setRedditClient(redditClient);
        }
    }

}
