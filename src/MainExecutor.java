
import ApiAndDb.ResetStaticData;
import ApiAndDb.SystemSettings;
import net.rithms.riot.constant.Region;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Renedix
 */
public class MainExecutor {
    
    /**
     * 
     * @param args 
     * [0] = Riot Api Key (REQUIRED)
     * [1] = SQL IP Port (REQUIRED)
     * [2] = SQL Master Username (REQUIRED)
     * [3] = SQL Master Password (REQUIRED)
     * [4] = Target Region (REQUIRED)
     * [5] = Summoner (REQUIRED)
     */
    public static void main(String[] args){
        
        // localhost
        // Hatred88
        // root
        // dfcc6cdf-2a61-4e24-a93f-3d2c0240e3ff
        
        args = new String[9];
        
        args[0] = "";
        args[1] = "localhost";
        args[2] = "";
        args[3] = "";
        args[4] = "league_player_stats";
        args[5] = "NA";
        args[6] = "";
        args[7] = "Y";
        args[8] = "150";
        
                
        if (args.length<9){
            
            System.out.println("Not all Required Parameters were provided!");
            
            String parameters = " [0] = Riot Api Key"+
                                "\n [1] = SQL IP Port"+
                                "\n [2] = SQL Master Username"+
                                "\n [3] = SQL Master Password"+
                                "\n [4] = Target Schema"+
                                "\n [5] = Target Region"+
                                "\n [6] = Summoner"+
                                "\n [7] = Include recent matches Y/N"+
                                "\n [8] = Ranked matches to retrieve";
            
            System.out.println(parameters);
        }else{
            execute(args[0],args[1],args[2],args[3],args[4],Region.valueOf(args[5]),args[6],args[7].equals("Y"),Integer.parseInt(args[8]));
        }
    }
    
    public static void setStaticData(){
        ResetStaticData resetStaticData = new ResetStaticData();
        resetStaticData.execute();
    }
    
    
    private static void execute(String apiKey,
                                String sqlIPPort,
                                String sqlUser,
                                String sqlPW,
                                String sqlSchema,
                                Region region,
                                String summonerName,
                                boolean includeRecentMatches,
                                int numberOfMatches){

        SystemSettings.riot_api_key = apiKey;
        SystemSettings.sql_ip_port = sqlIPPort;
        SystemSettings.sql_master_user = sqlUser;
        SystemSettings.sql_master_pw = sqlPW;
        SystemSettings.sql_schema = sqlSchema;

        ParameterValidityCodes result = validParameters(apiKey);

        if (result.errorMessage.isEmpty()){
            // Create schema if required
            
            // 
            
            SummonerScrapeWorker worker = new SummonerScrapeWorker(includeRecentMatches,numberOfMatches,summonerName,region);
            worker.run();
        }else{
            System.err.println(result.errorMessage+"\nPlease try again.");
        }
    }
    
    
    private static ParameterValidityCodes validParameters(String apiKey){
    
        boolean apiKeyIsValid = true;
        
        boolean databaseConnectionIsValid = true;
        
        boolean schemaIsPresent = true;
        
        boolean summonerExists = true;
        
        return ParameterValidityCodes.NOERROR;
    }
    
    private enum ParameterValidityCodes{
        NOERROR (""),
        INVALIDAPIKEY("The provided Riot API key is invalid."),
        INVALIDCONNECTION("The provided SQL connection details are invalid."),
        SCHEMADOESNOTEXIST("The provided schema does not exist in the database."),
        SUMMONERDOESNOTEXIST("Summoner does not exist for this region.");
        
        public String errorMessage;
        private ParameterValidityCodes(String errorMessage) {        
            this.errorMessage = errorMessage;
        }
    }
    
    
    
    
    
}
