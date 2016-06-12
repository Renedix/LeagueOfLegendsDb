
import ApiAndDb.ApiWrapper;
import ApiAndDb.Db.DatabaseIO;
import ApiAndDb.Db.MatchAlreadyAddedException;
import ApiAndDb.Db.NoRecordFoundException;
import java.sql.SQLException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JTextArea;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.constant.Region;
import net.rithms.riot.dto.Game.Game;
import net.rithms.riot.dto.Game.RecentGames;
import net.rithms.riot.dto.Match.MatchDetail;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Renedix
 */
class SummonerScrapeWorker implements Runnable{

        boolean getRecentMatches;
        int numberOfMatches;
        String summoner;
        Region region;

        public SummonerScrapeWorker(boolean getRecentMatches, 
                                    int numberOfMatches, 
                                    String summoner,
                                    Region region){
            this.getRecentMatches = getRecentMatches;
            this.numberOfMatches = numberOfMatches;
            this.summoner = summoner;
            this.region = region;
        }

        @Override
        public void run() {
            if (getRecentMatches){
                scrapeRecentMatches();
            }

            scrapeRankedMatches();

        }

        private void appendToConsole(String message){
            System.out.println("\n" + message);
        }

        private void scrapeRecentMatches(){
            appendToConsole("Storing latest games..");

            RiotApi api = ApiWrapper.api;

            appendToConsole("Getting summoner ID from API.");

            long summonerId = 0;
            try {
                summonerId = ApiWrapper.getSummonerId(summoner,region, api);

                int counter = 1;
                RecentGames recentGames = api.getRecentGames(summonerId);
                Set<Game> games = recentGames.getGames();
                for(Game game: games){
                    Thread.sleep(1500);
                    appendToConsole("Loading game "+counter+"/"+games.size());

                    long championId = game.getChampionId();
                    MatchDetail detail  = api.getMatch(game.getGameId(), true);
                    try {
                        DatabaseIO.insertSummonersInMatches(detail, summonerId, summoner, championId, game.getTeamId());
                    } catch (MatchAlreadyAddedException e) {
                        System.out.println("\nMatch "+detail.getMatchId()+" already exists in database.");
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    } catch (NoRecordFoundException e) {
                        e.printStackTrace();
                    }
                    counter++;
                }

            } catch (RiotApiException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        private void scrapeRankedMatches(){

            RiotApi api = ApiWrapper.api;

            try {

                int start= 0;
                int end = start + 15;

                long summonerId = 0;

                appendToConsole("Getting summoner ID from API.");
                summonerId = ApiWrapper.getSummonerId(summoner,region, api);

                while (end<=numberOfMatches){
                    ApiWrapper.storeMatchHistory(summonerId, api, start, end);

                    start=start+15;
                    end=end+15;
                }

            } catch (RiotApiException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();
            } catch (SQLException ex) {
                ex.printStackTrace();
            } catch (NoRecordFoundException ex) {
                ex.printStackTrace();
            }

            appendToConsole("Match history scrape complete!");

        }

        public void execute(){
            Thread t1 = new Thread(this);
            t1.start();
        }

    }
