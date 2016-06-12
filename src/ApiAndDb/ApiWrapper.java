package ApiAndDb;

import ApiAndDb.Db.DatabaseIO;
import ApiAndDb.Db.MatchAlreadyAddedException;
import ApiAndDb.Db.NoRecordFoundException;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.dto.Match.MatchDetail;
import net.rithms.riot.dto.MatchList.MatchList;
import net.rithms.riot.dto.MatchList.MatchReference;
import net.rithms.riot.dto.Summoner.Summoner;

import javax.swing.*;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import net.rithms.riot.constant.Region;

/**
 * Created with IntelliJ IDEA.
 * User: EmilyWindows
 * Date: 3/8/15
 * Time: 12:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class ApiWrapper {

    public static final RiotApi api = new RiotApi(SystemSettings.riot_api_key);

    public static long getSummonerId(String summonerName, Region region, RiotApi api) throws RiotApiException {
        long summonerId = 0;

        Summoner summoner = api.getSummonerByName(summonerName);
        summonerId = summoner.getId();

        return summonerId;
    }

    public static void storeMatchHistory(long summonerId, RiotApi api, int start, int end) throws RiotApiException, ClassNotFoundException, SQLException, NoRecordFoundException {
        System.out.println("Executing getMatchHistory for summoner:" + summonerId);

        MatchList matchList = null;
        try{
            System.out.println("Finding matches for for indexes: "+start+" to "+end);
            matchList = api.getMatchList(summonerId);
        }catch (RiotApiException e) {
            if (e.getErrorCode()==RiotApiException.RATE_LIMITED){
                try {
                    System.out.println("Rate limit reached. Waiting 1 minute before trying again.");
                    Thread.sleep(30000);
                    System.out.println("Rate limit reached. Waiting 30 seconds before trying again.");
                    Thread.sleep(30000);
                    matchList = api.getMatchList(summonerId);

                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }else{
                e.printStackTrace();
                return;
            }
        }


        List<MatchReference> matchReferences = matchList.getMatches();

        int counter = start+1;
        System.out.println("Iterating through matches..");
        if (matchReferences==null)
            return;

        for(Iterator<MatchReference> iter = matchReferences.iterator(); iter.hasNext();){
            System.out.println("Match " + (counter) + "/" + (end));
            MatchReference matchReference = iter.next();

            MatchDetail detail = null;
            try{
                detail  = api.getMatch(matchReference.getMatchId(), true);
            }catch (RiotApiException e) {
                if (e.getErrorCode()==RiotApiException.RATE_LIMITED){
                    try {
                        System.out.println("Rate limit reached. Waiting 1 minute before trying again.");
                        Thread.sleep(30000);
                        System.out.println("Rate limit reached. Waiting 30 seconds before trying again.");
                        Thread.sleep(30000);

                        detail  = api.getMatch(matchReference.getMatchId(), true);

                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }else if (e.getErrorCode()==RiotApiException.DATA_NOT_FOUND){
                    System.out.println("DATA_NOT_FOUND");
                    continue;
                }else if(e.getErrorCode()==RiotApiException.UNAVAILABLE){
                    System.out.println("Service unavailable. Waiting 5 seconds.");
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
                else{
                    e.printStackTrace();
                    return;
                }
            }

            try {
                DatabaseIO.insertSummonersInMatches(detail, summonerId, "", -1, -1);
            } catch (MatchAlreadyAddedException e) {
                System.out.println("Match "+detail.getMatchId()+" already exists in database.");
            }
            counter++;
        }

    }

}
