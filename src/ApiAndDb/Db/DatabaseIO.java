package ApiAndDb.Db;

import net.rithms.riot.dto.Match.*;
import net.rithms.riot.dto.Match.Mastery;
import net.rithms.riot.dto.Match.Rune;
import net.rithms.riot.dto.Static.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: EmilyWindows
 * Date: 2/15/15
 * Time: 5:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class DatabaseIO {

    public static void insertSummonersInMatches(MatchDetail detail, long targetSummonerId, String targetSummonerName, long targetChampionId, long targetTeamId) throws ClassNotFoundException, SQLException, NoRecordFoundException, MatchAlreadyAddedException {

        long matchId = detail.getMatchId();
        
        TransactionJdbcManager transactionJdbcManager = new TransactionJdbcManager();
        transactionJdbcManager.startTransaction();
        
        try{
            if(detail==null){
                return;
            }
    
            boolean matchExists = matchExists(matchId,transactionJdbcManager);
            boolean rankedMatch = targetChampionId==-1;
            
            if (matchExists){
                if (rankedMatch){
                    //ranked, do not check to update
                    transactionJdbcManager.close();
                    throw new MatchAlreadyAddedException();
                }else{
                    System.out.println("Exists but will update!"); 
                }
            }else{
                System.out.println("Processing match "+detail.getMatchId());
            }
    
            if (matchExists){
                System.out.println("Updating match");
                List<Participant> participants = detail.getParticipants();
    
                for(Iterator<Participant> iter = participants.iterator(); iter.hasNext();){
                    Participant participant = iter.next();
                    if (participant.getChampionId()==targetChampionId && participant.getTeamId() == targetTeamId){
                        long participantId = participant.getParticipantId();

                        insertSummoner(targetSummonerName, targetSummonerId,transactionJdbcManager);
                        updateSummoner(matchId,participantId,targetSummonerId,transactionJdbcManager);
                    }
                }

                transactionJdbcManager.commit();
                transactionJdbcManager.close();
                throw new MatchAlreadyAddedException();
            }else{
                System.out.println("Inserting match");
                //INSERT DATA
                insertGameMatch(matchId,detail,transactionJdbcManager);
        
                List<Participant> participants = detail.getParticipants();
        
                for(Iterator<Participant> iter = participants.iterator(); iter.hasNext();){
                    Participant participant = iter.next();
                    Player player = getPlayer(participant.getParticipantId(),detail);

                    long summonerId = -1;

                    if (targetTeamId != -1 && participant.getChampionId()==targetChampionId && participant.getTeamId() == targetTeamId){
                        insertSummoner(targetSummonerName, targetSummonerId,transactionJdbcManager);
                        summonerId = targetSummonerId;
                    }else if (targetTeamId!=-1){
                        insertSummoner("<Anonymous>", -1, transactionJdbcManager);
                        summonerId = getAnonymousSummonerId(transactionJdbcManager);
                    }else{
                        insertSummoner(player.getSummonerName(), player.getSummonerId(), transactionJdbcManager);
                        summonerId = player.getSummonerId();
                    }
        
                    insertSummonerMatch(summonerId, participant, matchId,transactionJdbcManager);

                    insertMasteries(participant.getMasteries(), matchId, participant.getParticipantId(),transactionJdbcManager);
                    insertRunes(participant.getRunes(),matchId,participant.getParticipantId(),transactionJdbcManager);
                    insertSummonerMatchStats(participant.getStats(),participant.getParticipantId(),matchId,transactionJdbcManager);
                }
                if (detail.getTimeline()!=null){
                    insertTimeline(detail.getTimeline(), matchId,transactionJdbcManager);
                }
            }

            // If we have made it this far without error, we can move the data from staging!
            moveDataFromStaging(transactionJdbcManager);

            // If this executed without error, clear out staing
            clearStagingRows(transactionJdbcManager);

        }catch (SQLException e){
            System.err.println("SQL error while inserting match "+matchId+".\n Rolling back query.");
            e.printStackTrace();
            transactionJdbcManager.rollback();
            transactionJdbcManager.close();
            return;
        }
        transactionJdbcManager.commit();
        System.out.println("COMMITTED!");
        transactionJdbcManager.close();

    }

    private static void clearStagingRows(TransactionJdbcManager jdbcManager) throws SQLException, ClassNotFoundException {
        executeSql("call clearStaging()",jdbcManager);
    }

    private static void updateSummoner(long matchId, long participantId, long targetSummonerId, TransactionJdbcManager jdbcManager) throws SQLException, ClassNotFoundException {
        String sql = "update staging_summonerMatch set summonerId = "+targetSummonerId+" where matchId = "+matchId+" and participantId = "+participantId+" and summonerId != "+targetSummonerId+";";

        executeSql(sql,jdbcManager);
    }

    private static long getAnonymousSummonerId(TransactionJdbcManager jdbcManager) throws NoRecordFoundException, SQLException, ClassNotFoundException {
        String qry = "select Id from summoner where name = '<Anonymous>' and Id = (select MAX(Id) from summoner where name = '<Anonymous>');";
        long summonerId = -1;

        ResultSet resultSet = jdbcManager.getResultSetFromQuery(qry);

        while (resultSet.next()){
            summonerId = resultSet.getLong("Id");
        }

        jdbcManager.closeStatement();

        return summonerId;
    }

    private static boolean matchExists(long matchId,TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException, NoRecordFoundException {
        boolean exists = false;

        String sql = "select case when exists(select 1 from gameMatch where Id = "+matchId+") then 1 else 0 end doesExist";
        ResultSet resultSet = jdbcManager.getResultSetFromQuery(sql);

        while(resultSet.next()){
            exists = (resultSet.getInt("doesExist")==1) ? true:false;
        }

        jdbcManager.closeStatement();

        return exists;
    }

    private static void insertSummoner(String summonerName, long summonerId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        try{
            String sql = "";
            if (summonerId!=-1){
                sql = "insert into summoner(id,name) values("+summonerId+",'"+summonerName+"');";
            }else{
                sql = "insert into summoner(name) values('"+summonerName+"');";
            }
            executeSql(sql,jdbcManager);
        }catch (SQLException exception){
            if(exception.getErrorCode()!=1062){
                throw exception;
            }
        }
    }

    private static void insertSummonerMatch(long sumonerId, Participant participant, long matchId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        String sql = "insert into staging_summonerMatch(summonerId,matchId,teamId,championId,highestRank,summonerSpell1Id,summonerSpell2Id,participantId) values("+sumonerId+","+matchId+","+participant.getTeamId()+","+participant.getChampionId()+",'"+participant.getHighestAchievedSeasonTier()+"',"+participant.getSpell1Id()+","+participant.getSpell2Id()+","+participant.getParticipantId()+");";

        executeSql(sql,jdbcManager);
    }

    private static void insertGameMatch(long matchId, MatchDetail detail, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        String sql = "insert into gameMatch(id,queueType,matchDate,matchMode, matchType, duration) values("+matchId+",'"+detail.getQueueType()+"',"+detail.getMatchCreation()+",'"+detail.getMatchMode()+"','"+detail.getMatchType()+"',"+detail.getMatchDuration()+")";

        executeSql(sql,jdbcManager);
    }

    private static void moveDataFromStaging(TransactionJdbcManager jdbcManager) throws SQLException, ClassNotFoundException {

        String sql = "call migrateAllMatchesFromStaging()";
        executeSql(sql,jdbcManager);
    }

    private static void insertTimeline(Timeline timeline, long matchId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException, NoRecordFoundException {
        String sql = "";
        List<Frame> frames = timeline.getFrames();

        for(Iterator<Frame> frameIterator = frames.iterator();frameIterator.hasNext();){
            Frame frame = frameIterator.next();
            long timestamp = frame.getTimestamp();

            insertFrame(matchId, timestamp,jdbcManager);

            long frameId = getFrameId(matchId,timestamp,jdbcManager);

            if (frame.getEvents()!=null){
                for(Iterator<Event> eventIterator = frame.getEvents().iterator();eventIterator.hasNext();){
                    Event event = eventIterator.next();
                    insertFrameEvent(event,frameId,jdbcManager);
                }
            }

            Map<String, ParticipantFrame> participantFrames = frame.getParticipantFrames();
            for(Map.Entry<String, ParticipantFrame> entry : participantFrames.entrySet() ){
                ParticipantFrame participantFrame = entry.getValue();
                if (participantFrame!=null){
                    insertFrameParticipant(participantFrame, frameId, matchId,jdbcManager);
                }
            }
        }
    }

    private static void insertFrameParticipant(ParticipantFrame participantFrame, long frameId, long matchId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        String sql = "insert into staging_frameParticipant("+
                "matchId,"+
                "frameId,"+
                "currentGold,"+
                "jungleMinionsKilled,"+
                "level,"+
                "minionsKilled,"+
                "participantId,"+
                "x,"+
                "y,"+
                "totalGold,"+
                "xp) values("+
                matchId+","+
                frameId+","+
                participantFrame.getCurrentGold()+","+
                participantFrame.getJungleMinionsKilled()+","+
                participantFrame.getLevel()+","+
                participantFrame.getMinionsKilled()+","+
                participantFrame.getParticipantId()+","+
                ((participantFrame.getPosition()==null)? 0 : participantFrame.getPosition().getX())+","+
                ((participantFrame.getPosition()==null)? 0 : participantFrame.getPosition().getY())+","+
                participantFrame.getTotalGold()+","+
                participantFrame.getXp()+");";
        executeSql(sql,jdbcManager);

    }

    private static void insertFrame(long matchId, long timestamp, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        String sql = "insert into staging_frame(matchId, timestamp) values("+matchId+","+timestamp+");";
        executeSql(sql,jdbcManager);
    }

    private static long getFrameId(long matchId, long timestamp, TransactionJdbcManager jdbcManager) throws NoRecordFoundException, SQLException, ClassNotFoundException {
        long Id = 0;
        String sql = "select Id from staging_frame where matchId = "+matchId+" and timestamp = "+timestamp;

        ResultSet resultSet = jdbcManager.getResultSetFromQuery(sql);

        while(resultSet.next()){
            Id = resultSet.getLong(1);
        }

        jdbcManager.closeStatement();

        return Id;
    }

    private static void insertFrameEvent(Event event, long frameId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException, NoRecordFoundException {

        String sql = "insert into staging_FrameEvent("+
                "frameId,"+
                "buildingType,"+
                "creatorId,"+
                "eventType,"+
                "itemAfter,"+
                "itemBefore,"+
                "itemId,"+
                "killerId,"+
                "laneType,"+
                "levelUpType,"+
                "monsterType,"+
                "participantId,"+
                "x,"+
                "y,"+
                "skillSlot,"+
                "teamId,"+
                "towerType,"+
                "victimId,"+
                "wardType,"+
                "timestamp) values("+
                frameId+","+
                "'"+event.getBuildingType()+"',"+
                event.getCreatorId()+","+
                "'"+event.getEventType()+"',"+
                event.getItemAfter()+","+
                event.getItemBefore()+","+
                event.getItemId()+","+
                event.getKillerId()+","+
                "'"+event.getLaneType()+"',"+
                "'"+event.getLevelUpType()+"',"+
                "'"+event.getMonsterType()+"',"+
                event.getParticipantId()+","+
                ((event.getPosition()==null) ? 0 : event.getPosition().getX())+","+
                ((event.getPosition()==null) ? 0 : event.getPosition().getY())+","+
                event.getSkillSlot()+","+
                event.getTeamId()+","+
                "'"+event.getTowerType()+"',"+
                event.getVictimId()+","+
                "'"+event.getWardType()+"',"+
                event.getTimestamp()+");";

        executeSql(sql,jdbcManager);

        long eventId = getLastEventId(jdbcManager);

        List<Integer> participantIds = event.getAssistingParticipantIds();
        if(participantIds!=null && !participantIds.isEmpty()){
            for(Iterator<Integer> participantIterator = participantIds.iterator();participantIterator.hasNext();){
                Integer participantId = participantIterator.next();
                sql = "insert into staging_FrameEventParticipant(eventId,participantId) values("+eventId+","+participantId+");";
                executeSql(sql,jdbcManager);
            }
        }

    }

    private static long getLastEventId(TransactionJdbcManager jdbcManager) throws NoRecordFoundException, SQLException {

        String sql = "select max(Id) Id from staging_FrameEvent ";
        long id = 0;
        ResultSet resultSet = jdbcManager.getResultSetFromQuery(sql);

        while(resultSet.next()){
            id = resultSet.getLong("Id");
        }

        return id;
    }


    private static void insertSummonerMatchStats(ParticipantStats stats, int participantId, long matchId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        String sql = "";

        sql = "insert into staging_summonerMatchStats("+
        "participantId,"+
        "        matchId,"+
        "        assists,"+
        "        championLevel,"+
        "        combatPlayerScore,"+
        "        deaths,"+
        "        doubleKills,"+
        "        firstBloodAssist,"+
        "        firstBloodKill,"+
        "        firstInhibitorAssist,"+
        "        firstInhibitorKill,"+
        "        firstTowerAssist,"+
        "        firstTowerKill,"+
        "        goldEarned,"+
        "        goldSpent,"+
        "        inhibitorKills,"+
        "        item0,"+
        "        item1,"+
        "        item2,"+
        "        item3,"+
        "        item4,"+
        "        item5,"+
        "        item6,"+
        "        killingSprees,"+
        "        kills,"+
        "        largestCriticalStrike,"+
        "        largestKillingSpree,"+
        "        largestMultiKill,"+
        "        magicDamageDealt,"+
        "        magicDamageDealtToChampions,"+
        "        magicDamageTaken,"+
        "        minionsKilled,"+
        "        neutralMinionsKilled,"+
        "        neutralMinionsKilledEnemyJungle,"+
        "        neutralMinionsKilledTeamJungle,"+
        "        nodeCapture,"+
        "        nodeCaptureAssist,"+
        "        nodeNeutralize,"+
        "        nodeNeutralizeAssist,"+
        "        objectivePlayerScore,"+
        "        pentaKills,"+
        "        physicalDamageDealt,"+
        "        physicalDamageDealtToChampions,"+
        "        physicalDamageTaken,"+
        "        quadraKills,"+
        "        sightWardsBoughtInGame,"+
        "        teamObjective,"+
        "        totalDamageDealt,"+
        "        totalDamageDealtToChampions,"+
        "        totalDamageTaken,"+
        "        totalHeal,"+
        "        totalPlayerScore,"+
        "        totalScoreRank,"+
        "        totalTimeCrowdControlDealt,"+
        "        totalUnitsHealed,"+
        "        towerKills,"+
        "        tripleKills,"+
        "        trueDamageDealt,"+
        "        trueDamageDealtToChampions,"+
        "        trueDamageTaken,"+
        "        unrealKills,"+
        "        visionWardsBoughtInGame,"+
        "        wardsKilled,"+
        "        wardsPlaced,"+
        "        winner)"+
        "values("+
                participantId+","+
                matchId+","+
                stats.getAssists()+","+
                stats.getChampLevel()+","+
                stats.getCombatPlayerScore()+","+
                stats.getDeaths()+","+
                stats.getDoubleKills()+","+
                stats.isFirstBloodAssist()+","+
                stats.isFirstBloodKill()+","+
                stats.isFirstInhibitorAssist()+","+
                stats.isFirstInhibitorKill()+","+
                stats.isFirstTowerAssist()+","+
                stats.isFirstTowerKill()+","+
                stats.getGoldEarned()+","+
                stats.getGoldSpent()+","+
                stats.getInhibitorKills()+","+
                stats.getItem0()+","+
                stats.getItem1()+","+
                stats.getItem2()+","+
                stats.getItem3()+","+
                stats.getItem4()+","+
                stats.getItem5()+","+
                stats.getItem6()+","+
                stats.getKillingSprees()+","+
                stats.getKills()+","+
                stats.getLargestCriticalStrike()+","+
                stats.getLargestKillingSpree()+","+
                stats.getLargestMultiKill()+","+
                stats.getMagicDamageDealt()+","+
                stats.getMagicDamageDealtToChampions()+","+
                stats.getMagicDamageTaken()+","+
                stats.getMinionsKilled()+","+
                stats.getNeutralMinionsKilled()+","+
                stats.getNeutralMinionsKilledEnemyJungle()+","+
                stats.getNeutralMinionsKilledTeamJungle()+","+
                stats.getNodeCapture()+","+
                stats.getNodeCaptureAssist()+","+
                stats.getNodeNeutralize()+","+
                stats.getNodeNeutralizeAssist()+","+
                stats.getObjectivePlayerScore()+","+
                stats.getPentaKills()+","+
                stats.getPhysicalDamageDealt()+","+
                stats.getPhysicalDamageDealtToChampions()+","+
                stats.getPhysicalDamageTaken()+","+
                stats.getQuadraKills()+","+
                stats.getSightWardsBoughtInGame()+","+
                stats.getTeamObjective()+","+
                stats.getTotalDamageDealt()+","+
                stats.getTotalDamageDealtToChampions()+","+
                stats.getTotalDamageTaken()+","+
                stats.getTotalHeal()+","+
                stats.getTotalPlayerScore()+","+
                stats.getTotalScoreRank()+","+
                stats.getTotalTimeCrowdControlDealt()+","+
                stats.getTotalUnitsHealed()+","+
                stats.getTowerKills()+","+
                stats.getTripleKills()+","+
                stats.getTrueDamageDealt()+","+
                stats.getTrueDamageDealtToChampions()+","+
                stats.getTrueDamageTaken()+","+
                stats.getUnrealKills()+","+
                stats.getVisionWardsBoughtInGame()+","+
                stats.getWardsKilled()+","+
                stats.getWardsPlaced()+","+
                stats.isWinner()+
        ");";

        executeSql(sql,jdbcManager);
    }

    private static void insertRunes(List<Rune> runes, long matchId, int participantId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        //summonerMatchRune
        String sql = "";
        if(runes==null){
            return;
        }

        for(Iterator<Rune> runeIterator = runes.iterator();runeIterator.hasNext();){
            Rune rune = runeIterator.next();
            sql = "insert into staging_summonerMatchRune(participantId, matchId, runeId, count) values("+participantId+","+matchId+","+rune.getRuneId()+","+rune.getRank()+");";
            executeSql(sql,jdbcManager);
        }

    }

    private static void insertMasteries(List<Mastery> masteries, long matchId, int participantId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        //java.util.List<dto.Match.Mastery>
        //summonerMatchMastery
        String sql = "";
        if (masteries==null){
            return;
        }

        for(Iterator<Mastery> masteryIterator = masteries.iterator();masteryIterator.hasNext();){
            Mastery mastery = masteryIterator.next();
            sql = "insert into staging_summonerMatchMastery(participantId, matchId, masteryId, rank) values ("+participantId+","+matchId+","+mastery.getMasteryId()+","+mastery.getRank()+");";

            executeSql(sql,jdbcManager);

        }
    }

    private static void executeSql(String sql, TransactionJdbcManager jdbcManager) throws SQLException, ClassNotFoundException {

        try{
            jdbcManager.executeQuery(sql);
        }catch (SQLException e){
            System.err.println(sql);
            throw e;
        }
    }


    private static Player getPlayer(long participantId, MatchDetail detail){

        List<ParticipantIdentity> participantIdentities = detail.getParticipantIdentities();

        for(Iterator<ParticipantIdentity> iter = participantIdentities.iterator(); iter.hasNext();){
            ParticipantIdentity participantIdentity = iter.next();
            if(participantIdentity.getParticipantId()==participantId){
                return participantIdentity.getPlayer();
            }
        }

        return null;
    }

    private static void deleteMatchData(long matchId, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        String sql = "delete from staging_summonerMatch where matchId = "+matchId;

        executeSql(sql,jdbcManager);

        sql = "delete from gameMatch where id = "+matchId;

        executeSql(sql,jdbcManager);

        sql = "delete from staging_frameEvent where frameId in(select Id from staging_frame where matchId = "+matchId+")";

        executeSql(sql,jdbcManager);

        sql = "delete from staging_FrameParticipant where frameId in(select Id from staging_frame where matchId = "+matchId+")";

        executeSql(sql,jdbcManager);

        sql = "delete from staging_frame where matchId = "+matchId;

        executeSql(sql,jdbcManager);

        sql = "delete from staging_summonerMatchMastery where matchId = "+matchId;

        executeSql(sql,jdbcManager);

        sql = "delete from staging_summonerMatchStats where matchId = "+matchId;

        executeSql(sql,jdbcManager);

        sql = "delete from staging_summonerMatchRune where matchId = "+matchId;
        executeSql(sql,jdbcManager);

    }

    public static  void refreshChampionData(net.rithms.riot.dto.Static.ChampionList champions, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        String sql = "";
        Map<String, net.rithms.riot.dto.Static.Champion> championList = champions.getData();

        sql = "delete from champion";
        executeSql(sql,jdbcManager);

        for(Map.Entry<String, net.rithms.riot.dto.Static.Champion> entry: championList.entrySet()){
            net.rithms.riot.dto.Static.Champion champion = entry.getValue();

            sql = "insert into champion(id,name) values("+champion.getId()+",'"+champion.getName().replaceAll("'"," ")+"');";
            executeSql(sql,jdbcManager);
        }

    }

    public static void refreshSummonerSpellList(SummonerSpellList summonerSpellList, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        Map<String,SummonerSpell> map = summonerSpellList.getData();
        String sql = "delete from summonerSpell";
        executeSql(sql,jdbcManager);

        for(Map.Entry<String,SummonerSpell> entry: map.entrySet()){
            SummonerSpell summonerSpell = entry.getValue();
            sql = "insert into summonerSpell(id,name) values("+summonerSpell.getId()+",'"+summonerSpell.getName()+"');";
            executeSql(sql,jdbcManager);
        }

    }

    public static void refreshMasteryList(net.rithms.riot.dto.Static.MasteryList masteryList, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        Map<String, net.rithms.riot.dto.Static.Mastery> masteryMap = masteryList.getData();
        String sql = "delete from summonerMastery";
        executeSql(sql,jdbcManager);
        for(Map.Entry<String, net.rithms.riot.dto.Static.Mastery> entry: masteryMap.entrySet()){
            net.rithms.riot.dto.Static.Mastery mastery = entry.getValue();
            sql = "insert into summonerMastery(id,name,description,totalRanks) values("+mastery.getId()+",'"+mastery.getName().replaceAll("'","")+"','"+mastery.getDescription().toString().replaceAll("'","")+"',"+mastery.getRanks()+");";
            executeSql(sql,jdbcManager);
        }
    }

    public static void refreshRuneList(net.rithms.riot.dto.Static.RuneList runelist, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        Map<String, net.rithms.riot.dto.Static.Rune> runes = runelist.getData();

        for(Map.Entry<String, net.rithms.riot.dto.Static.Rune> entry: runes.entrySet()){
            net.rithms.riot.dto.Static.Rune rune = entry.getValue();

            String sql = "delete from summonerRune where id = "+rune.getId();
            executeSql(sql,jdbcManager);

            BasicDataStats stats = rune.getStats();

            sql =   "insert into summonerRune(id,name,description,type," +
                    "FlatArmorMod,"+
                    "FlatAttackSpeedMod,"+
                    "FlatBlockMod,"+
                    "FlatCritChanceMod,"+
                    "FlatCritDamageMod,"+
                    "FlatEXPBonus,"+
                    "FlatEnergyPoolMod,"+
                    "FlatEnergyRegenMod,"+
                    "FlatHPPoolMod,"+
                    "FlatHPRegenMod,"+
                    "FlatMPPoolMod,"+
                    "FlatMPRegenMod,"+
                    "FlatMagicDamageMod,"+
                    "FlatMovementSpeedMod,"+
                    "FlatPhysicalDamageMod,"+
                    "FlatSpellBlockMod,"+
                    "PercentArmorMod,"+
                    "PercentAttackSpeedMod,"+
                    "PercentBlockMod,"+
                    "PercentCritChanceMod,"+
                    "PercentCritDamageMod,"+
                    "PercentDodgeMod,"+
                    "PercentEXPBonus,"+
                    "PercentHPPoolMod,"+
                    "PercentHPRegenMod,"+
                    "PercentLifeStealMod,"+
                    "PercentMPPoolMod,"+
                    "PercentMPRegenMod,"+
                    "PercentMagicDamageMod,"+
                    "PercentMovementSpeedMod,"+
                    "PercentPhysicalDamageMod,"+
                    "PercentSpellBlockMod,"+
                    "PercentSpellVampMod,"+
                    "rFlatArmorModPerLevel,"+
                    "rFlatArmorPenetrationMod,"+
                    "rFlatArmorPenetrationModPerLevel,"+
                    "rFlatCritChanceModPerLevel,"+
                    "rFlatCritDamageModPerLevel,"+
                    "rFlatDodgeMod,"+
                    "rFlatDodgeModPerLevel,"+
                    "rFlatEnergyModPerLevel,"+
                    "rFlatEnergyRegenModPerLevel,"+
                    "rFlatGoldPer10Mod,"+
                    "rFlatHPModPerLevel,"+
                    "rFlatHPRegenModPerLevel,"+
                    "rFlatMPModPerLevel,"+
                    "rFlatMPRegenModPerLevel,"+
                    "rFlatMagicDamageModPerLevel,"+
                    "rFlatMagicPenetrationMod,"+
                    "rFlatMagicPenetrationModPerLevel,"+
                    "rFlatMovementSpeedModPerLevel,"+
                    "rFlatPhysicalDamageModPerLevel,"+
                    "rFlatSpellBlockModPerLevel,"+
                    "rFlatTimeDeadMod,"+
                    "rFlatTimeDeadModPerLevel,"+
                    "rPercentArmorPenetrationMod,"+
                    "rPercentArmorPenetrationModPerLevel,"+
                    "rPercentAttackSpeedModPerLevel,"+
                    "rPercentCooldownMod,"+
                    "rPercentCooldownModPerLevel,"+
                    "rPercentMagicPenetrationMod,"+
                    "rPercentMagicPenetrationModPerLevel,"+
                    "rPercentMovementSpeedModPerLevel,"+
                    "rPercentTimeDeadMod,"+
                    "rPercentTimeDeadModPerLevel) " +
                    "values("+rune.getId()+",'"+
                    rune.getName().replaceAll("'","")+"','"+
                    rune.getDescription().replaceAll("'","")+"','"+
                    rune.getRune().getType()+"',"+
                    stats.getFlatArmorMod()+","+
                    stats.getFlatAttackSpeedMod()+","+
                    stats.getFlatBlockMod()+","+
                    stats.getFlatCritChanceMod()+","+
                    stats.getFlatCritDamageMod()+","+
                    stats.getFlatEXPBonus()+","+
                    stats.getFlatEnergyPoolMod()+","+
                    stats.getFlatEnergyRegenMod()+","+
                    stats.getFlatHPPoolMod()+","+
                    stats.getFlatHPRegenMod()+","+
                    stats.getFlatMPPoolMod()+","+
                    stats.getFlatMPRegenMod()+","+
                    stats.getFlatMagicDamageMod()+","+
                    stats.getFlatMovementSpeedMod()+","+
                    stats.getFlatPhysicalDamageMod()+","+
                    stats.getFlatSpellBlockMod()+","+
                    stats.getPercentArmorMod()+","+
                    stats.getPercentAttackSpeedMod()+","+
                    stats.getPercentBlockMod()+","+
                    stats.getPercentCritChanceMod()+","+
                    stats.getPercentCritDamageMod()+","+
                    stats.getPercentDodgeMod()+","+
                    stats.getPercentEXPBonus()+","+
                    stats.getPercentHPPoolMod()+","+
                    stats.getPercentHPRegenMod()+","+
                    stats.getPercentLifeStealMod()+","+
                    stats.getPercentMPPoolMod()+","+
                    stats.getPercentMPRegenMod()+","+
                    stats.getPercentMagicDamageMod()+","+
                    stats.getPercentMovementSpeedMod()+","+
                    stats.getPercentPhysicalDamageMod()+","+
                    stats.getPercentSpellBlockMod()+","+
                    stats.getPercentSpellVampMod()+","+
                    stats.getrFlatArmorModPerLevel()+","+
                    stats.getrFlatArmorPenetrationMod()+","+
                    stats.getrFlatArmorPenetrationModPerLevel()+","+
                    stats.getrFlatCritChanceModPerLevel()+","+
                    stats.getrFlatCritDamageModPerLevel()+","+
                    stats.getrFlatDodgeMod()+","+
                    stats.getrFlatDodgeModPerLevel()+","+
                    stats.getrFlatEnergyModPerLevel()+","+
                    stats.getrFlatEnergyRegenModPerLevel()+","+
                    stats.getrFlatGoldPer10Mod()+","+
                    stats.getrFlatHPModPerLevel()+","+
                    stats.getrFlatHPRegenModPerLevel()+","+
                    stats.getrFlatMPModPerLevel()+","+
                    stats.getrFlatMPRegenModPerLevel()+","+
                    stats.getrFlatMagicDamageModPerLevel()+","+
                    stats.getrFlatMagicPenetrationMod()+","+
                    stats.getrFlatMagicPenetrationModPerLevel()+","+
                    stats.getrFlatMovementSpeedModPerLevel()+","+
                    stats.getrFlatPhysicalDamageModPerLevel()+","+
                    stats.getrFlatSpellBlockModPerLevel()+","+
                    stats.getrFlatTimeDeadMod()+","+
                    stats.getrFlatTimeDeadModPerLevel()+","+
                    stats.getrPercentArmorPenetrationMod()+","+
                    stats.getrPercentArmorPenetrationModPerLevel()+","+
                    stats.getrPercentAttackSpeedModPerLevel()+","+
                    stats.getrPercentCooldownMod()+","+
                    stats.getrPercentCooldownModPerLevel()+","+
                    stats.getrPercentMagicPenetrationMod()+","+
                    stats.getrPercentMagicPenetrationModPerLevel()+","+
                    stats.getrPercentMovementSpeedModPerLevel()+","+
                    stats.getrPercentTimeDeadMod()+","+
                    stats.getrPercentTimeDeadModPerLevel()+
                    ");";
            executeSql(sql,jdbcManager);
        }

    }

    public static void refreshItemList(net.rithms.riot.dto.Static.ItemList itemList, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {

        Map<java.lang.String,net.rithms.riot.dto.Static.Item> itemMap = itemList.getData();

        for(Map.Entry<java.lang.String,net.rithms.riot.dto.Static.Item> entry: itemMap.entrySet()){
            net.rithms.riot.dto.Static.Item item = entry.getValue();
            insertItem(item,jdbcManager);
        }

    }

    private static void insertItem(net.rithms.riot.dto.Static.Item item, TransactionJdbcManager jdbcManager) throws ClassNotFoundException, SQLException {
        String sql = "delete from summonerItem where Id = "+item.getId();
        executeSql(sql,jdbcManager);

        BasicDataStats stats = item.getStats();

        if (item.getName()==null){
            return;
        }


        sql =   "insert into summonerItem (id,name,totalPrice,basePrice,refundPrice," +
                "FlatArmorMod,"+
                "FlatAttackSpeedMod,"+
                "FlatBlockMod,"+
                "FlatCritChanceMod,"+
                "FlatCritDamageMod,"+
                "FlatEXPBonus,"+
                "FlatEnergyPoolMod,"+
                "FlatEnergyRegenMod,"+
                "FlatHPPoolMod,"+
                "FlatHPRegenMod,"+
                "FlatMPPoolMod,"+
                "FlatMPRegenMod,"+
                "FlatMagicDamageMod,"+
                "FlatMovementSpeedMod,"+
                "FlatPhysicalDamageMod,"+
                "FlatSpellBlockMod,"+
                "PercentArmorMod,"+
                "PercentAttackSpeedMod,"+
                "PercentBlockMod,"+
                "PercentCritChanceMod,"+
                "PercentCritDamageMod,"+
                "PercentDodgeMod,"+
                "PercentEXPBonus,"+
                "PercentHPPoolMod,"+
                "PercentHPRegenMod,"+
                "PercentLifeStealMod,"+
                "PercentMPPoolMod,"+
                "PercentMPRegenMod,"+
                "PercentMagicDamageMod,"+
                "PercentMovementSpeedMod,"+
                "PercentPhysicalDamageMod,"+
                "PercentSpellBlockMod,"+
                "PercentSpellVampMod,"+
                "rFlatArmorModPerLevel,"+
                "rFlatArmorPenetrationMod,"+
                "rFlatArmorPenetrationModPerLevel,"+
                "rFlatCritChanceModPerLevel,"+
                "rFlatCritDamageModPerLevel,"+
                "rFlatDodgeMod,"+
                "rFlatDodgeModPerLevel,"+
                "rFlatEnergyModPerLevel,"+
                "rFlatEnergyRegenModPerLevel,"+
                "rFlatGoldPer10Mod,"+
                "rFlatHPModPerLevel,"+
                "rFlatHPRegenModPerLevel,"+
                "rFlatMPModPerLevel,"+
                "rFlatMPRegenModPerLevel,"+
                "rFlatMagicDamageModPerLevel,"+
                "rFlatMagicPenetrationMod,"+
                "rFlatMagicPenetrationModPerLevel,"+
                "rFlatMovementSpeedModPerLevel,"+
                "rFlatPhysicalDamageModPerLevel,"+
                "rFlatSpellBlockModPerLevel,"+
                "rFlatTimeDeadMod,"+
                "rFlatTimeDeadModPerLevel,"+
                "rPercentArmorPenetrationMod,"+
                "rPercentArmorPenetrationModPerLevel,"+
                "rPercentAttackSpeedModPerLevel,"+
                "rPercentCooldownMod,"+
                "rPercentCooldownModPerLevel,"+
                "rPercentMagicPenetrationMod,"+
                "rPercentMagicPenetrationModPerLevel,"+
                "rPercentMovementSpeedModPerLevel,"+
                "rPercentTimeDeadMod,"+
                "rPercentTimeDeadModPerLevel) " +
                "values("+item.getId()+",'"+
                item.getName().replaceAll("'","")+"',"+
                item.getGold().getTotal()+","+
                item.getGold().getBase()+","+
                item.getGold().getSell()+","+
                stats.getFlatArmorMod()+","+
                stats.getFlatAttackSpeedMod()+","+
                stats.getFlatBlockMod()+","+
                stats.getFlatCritChanceMod()+","+
                stats.getFlatCritDamageMod()+","+
                stats.getFlatEXPBonus()+","+
                stats.getFlatEnergyPoolMod()+","+
                stats.getFlatEnergyRegenMod()+","+
                stats.getFlatHPPoolMod()+","+
                stats.getFlatHPRegenMod()+","+
                stats.getFlatMPPoolMod()+","+
                stats.getFlatMPRegenMod()+","+
                stats.getFlatMagicDamageMod()+","+
                stats.getFlatMovementSpeedMod()+","+
                stats.getFlatPhysicalDamageMod()+","+
                stats.getFlatSpellBlockMod()+","+
                stats.getPercentArmorMod()+","+
                stats.getPercentAttackSpeedMod()+","+
                stats.getPercentBlockMod()+","+
                stats.getPercentCritChanceMod()+","+
                stats.getPercentCritDamageMod()+","+
                stats.getPercentDodgeMod()+","+
                stats.getPercentEXPBonus()+","+
                stats.getPercentHPPoolMod()+","+
                stats.getPercentHPRegenMod()+","+
                stats.getPercentLifeStealMod()+","+
                stats.getPercentMPPoolMod()+","+
                stats.getPercentMPRegenMod()+","+
                stats.getPercentMagicDamageMod()+","+
                stats.getPercentMovementSpeedMod()+","+
                stats.getPercentPhysicalDamageMod()+","+
                stats.getPercentSpellBlockMod()+","+
                stats.getPercentSpellVampMod()+","+
                stats.getrFlatArmorModPerLevel()+","+
                stats.getrFlatArmorPenetrationMod()+","+
                stats.getrFlatArmorPenetrationModPerLevel()+","+
                stats.getrFlatCritChanceModPerLevel()+","+
                stats.getrFlatCritDamageModPerLevel()+","+
                stats.getrFlatDodgeMod()+","+
                stats.getrFlatDodgeModPerLevel()+","+
                stats.getrFlatEnergyModPerLevel()+","+
                stats.getrFlatEnergyRegenModPerLevel()+","+
                stats.getrFlatGoldPer10Mod()+","+
                stats.getrFlatHPModPerLevel()+","+
                stats.getrFlatHPRegenModPerLevel()+","+
                stats.getrFlatMPModPerLevel()+","+
                stats.getrFlatMPRegenModPerLevel()+","+
                stats.getrFlatMagicDamageModPerLevel()+","+
                stats.getrFlatMagicPenetrationMod()+","+
                stats.getrFlatMagicPenetrationModPerLevel()+","+
                stats.getrFlatMovementSpeedModPerLevel()+","+
                stats.getrFlatPhysicalDamageModPerLevel()+","+
                stats.getrFlatSpellBlockModPerLevel()+","+
                stats.getrFlatTimeDeadMod()+","+
                stats.getrFlatTimeDeadModPerLevel()+","+
                stats.getrPercentArmorPenetrationMod()+","+
                stats.getrPercentArmorPenetrationModPerLevel()+","+
                stats.getrPercentAttackSpeedModPerLevel()+","+
                stats.getrPercentCooldownMod()+","+
                stats.getrPercentCooldownModPerLevel()+","+
                stats.getrPercentMagicPenetrationMod()+","+
                stats.getrPercentMagicPenetrationModPerLevel()+","+
                stats.getrPercentMovementSpeedModPerLevel()+","+
                stats.getrPercentTimeDeadMod()+","+
                stats.getrPercentTimeDeadModPerLevel()+
                ");";
        executeSql(sql,jdbcManager);

    }

}
