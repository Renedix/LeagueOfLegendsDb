package ApiAndDb;

import ApiAndDb.Db.DatabaseIO;
import ApiAndDb.Db.TransactionJdbcManager;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.constant.Region;
import net.rithms.riot.constant.staticdata.ItemListData;
import net.rithms.riot.constant.staticdata.RuneListData;
import net.rithms.riot.dto.Static.*;

import javax.swing.*;
import java.sql.SQLException;

/**
 * Created with IntelliJ IDEA.
 * User: EmilyWindows
 * Date: 2/21/15
 * Time: 5:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class ResetStaticData implements Runnable{

    public void execute(){
        Thread t1 = new Thread(this);
        t1.start();
    }

    @Override
    public void run() {

        JOptionPane.showMessageDialog(null, "Resettting Static Data..");

        RiotApi api = ApiWrapper.api;
        
        System.out.println("Refreshing static data");

        System.out.println("...getDataChampionList");

        TransactionJdbcManager jdbcManager = null;

        ChampionList champions = null;
        try {
            jdbcManager = new TransactionJdbcManager();
            jdbcManager.startTransaction();

            champions = api.getDataChampionList();
            DatabaseIO.refreshChampionData(champions,jdbcManager);

            
            System.out.println("...getDataSummonerSpellList");

            SummonerSpellList summonerSpellList = api.getDataSummonerSpellList();
            DatabaseIO.refreshSummonerSpellList(summonerSpellList,jdbcManager);

            
            System.out.println("...getDataMasteryList");

            MasteryList masteryList = api.getDataMasteryList();
            DatabaseIO.refreshMasteryList(masteryList,jdbcManager);

            
            System.out.println("...getDataRuneList");

            RuneList runelist = api.getDataRuneList(Region.NA,null,null, RuneListData.ALL);
            DatabaseIO.refreshRuneList(runelist,jdbcManager);
            
            System.out.println("...getDataItemList");
            System.out.println("Static data refresh complete.");

            ItemList itemList = api.getDataItemList(Region.NA,null,null, ItemListData.ALL);
            DatabaseIO.refreshItemList(itemList,jdbcManager);

            JOptionPane.showMessageDialog(null, "Resetting static data complete!");

        } catch (RiotApiException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
            assert jdbcManager != null;
            try {
                jdbcManager.rollback();
                jdbcManager.close();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        }
        assert jdbcManager != null;
        try {
            jdbcManager.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
