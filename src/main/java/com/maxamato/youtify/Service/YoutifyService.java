package com.maxamato.youtify.Service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.maxamato.youtify.Credentials;
import com.maxamato.youtify.connection.SpotifyConnection;
import lombok.AllArgsConstructor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

import static com.maxamato.youtify.connection.YoutubeConnection.getService;


@Service
@AllArgsConstructor
public class YoutifyService {

    private final SpotifyConnection spotifyConnection;


    /***
     * TODO: fetch tracks better
     **/

    public String mainYoutify() throws IOException, ParseException, GeneralSecurityException {
        if(!doesYoutifyPlaylistExist()){
            createYoutifyPlaylist();
        }
        searchForTrackBasedOnPopularitySpotify(obtainTitlesFromYoutubePlaylist());

        return "You can close the window.";
    }

    private void createYoutifyPlaylist() throws IOException, ParseException {
        spotifyConnection.createYoutifyPlaylist();
    }

    private Boolean doesYoutifyPlaylistExist() throws IOException, ParseException {
        String jsonString = spotifyConnection.getPlaylists();
        JSONObject jsonObject = (JSONObject) new JSONParser().parse(jsonString);
        JSONArray jsonArray = (JSONArray) jsonObject.get("items");
        for (Object o : jsonArray) {
            JSONObject record = (JSONObject) o;
            String name = record.get("name").toString().toLowerCase();
            if (name.equals(Credentials.getPlName().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     Function searches for tracks in Spotify from your Youtube's playlist based on the popularity index.
     Keep in mind\, that the most popular track, may not be the one you are looking for.
     Spotify's API search item function does not wor.
    **/
    private void searchForTrackBasedOnPopularitySpotify(List<String> ytTracks) throws IOException, ParseException, GeneralSecurityException {
        List<String> ids=new ArrayList<>();
        for(String ytTrack:ytTracks) {
            String jsonString = spotifyConnection.searchForTrackBasedOnPopularity(ytTrack
                    .replace("- ", "").replace(" ", "%2B"));
            JSONObject jsonObject = (JSONObject) new JSONParser().parse(jsonString);
            JSONObject tracks = (JSONObject) jsonObject.get("tracks");
            JSONArray items = (JSONArray) tracks.get("items");
            List<Long> popularityValues = new ArrayList<>();
            for (Object o : items) {
                JSONObject toCompare = (JSONObject) o;
                popularityValues.add((Long) toCompare.get("popularity"));
            }
            int indexOfTheMostPopular = popularityValues.indexOf(Collections.max(popularityValues));
            JSONObject item = (JSONObject) items.get(indexOfTheMostPopular);
            String trackId = (String) item.get("id");
            ids.add(trackId);
        }
        addTracks(Credentials.getPlId(), ids);
    }
    
    
    /**
     * @param plId Playlists ID from spotify
     * @param trackId Track ID from spotify to add
     * **/
    private void addTracks(String plId, List<String> trackId) throws IOException, ParseException {
        String jsonString = spotifyConnection.getItemsFromPlaylist(plId);
        JSONObject jsonObject = (JSONObject) new JSONParser().parse(jsonString);
        JSONArray jsonArray = (JSONArray) jsonObject.get("items");
        List<String> spotifyIds = new ArrayList<>();
        for(Object o:jsonArray){
            JSONObject item = (JSONObject) o;
            JSONObject track = (JSONObject) item.get("track");
            String id = (String) track.get("id");
            spotifyIds.add(id);
        }
        List<String> intersection = new ArrayList<>(trackId);
        intersection.retainAll(spotifyIds);
        trackId.removeAll(intersection);
        trackId = trackId.stream().map(s -> "spotify:track:" + s).collect(Collectors.toList());
        spotifyConnection.addTracks(plId, trackId);

    }


    private List<String> obtainTitlesFromYoutubePlaylist()throws GeneralSecurityException, IOException, GoogleJsonResponseException {
        YouTube youtubeService = getService();
        // Define and execute the API request
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                .list(Collections.singletonList("snippet,contentDetails"));
        PlaylistItemListResponse response = request.setMaxResults(25L)
                .setPlaylistId(obtainYoutubePlaylistId(youtubeService))
                .execute();
        List<PlaylistItem> items = response.getItems();
        List<String> result = new ArrayList<>();
        for(PlaylistItem playlistItem:items){
            result.add(playlistItem.getSnippet().getTitle());
        }
        return result;
    }

    /**
     * @param youtubeService Youtube service entity - obtained through following google docs for Youtube API v3
     * @return String Playlists id
     * */
    private String obtainYoutubePlaylistId(YouTube youtubeService) throws GeneralSecurityException, IOException {
        // Define and execute the API request
        YouTube.Playlists.List request = youtubeService.playlists()
                .list(Collections.singletonList("snippet,contentDetails"));
        PlaylistListResponse response = request.setMaxResults(25L)
                .setMine(true)
                .execute();
        List<Playlist> items = response.getItems();
        String playlistName = Credentials.getPlName().toLowerCase();
        for(Playlist playlist:items){
            if(playlist.getSnippet().getTitle().toLowerCase().equals(playlistName)){
                return playlist.getId();
            }
        }
        throw new IllegalStateException(new Exception(String.format("No \"%s\" playlist found", playlistName)));
    }






}
