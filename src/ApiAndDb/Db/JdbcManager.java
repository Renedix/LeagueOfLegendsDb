package ApiAndDb.Db;

import ApiAndDb.SystemSettings;

import java.sql.*;

public class JdbcManager {

    Connection connection = null;
    Statement statement = null;

    String query;

    PreparedStatement preparedStatement =  null;
    Object[] parameters;
    ResultSet resultSet;
    boolean prepareSql;

    public JdbcManager() throws ClassNotFoundException, SQLException {
        this.prepareSql = false;
    }

    public JdbcManager(String query, boolean scrollable) throws ClassNotFoundException, SQLException{
        this.query = query;
        this.prepareSql = false;
        init(scrollable);
    }

    public JdbcManager(String query, Object[] parameters) throws ClassNotFoundException, SQLException{
        this.query = query;
        this.prepareSql = true;
        this.parameters = parameters;
        if(this.parameters==null){
            this.parameters = new Object[0];
        }
        init(true);
    }

    protected void initConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");

        connection = DriverManager.getConnection("jdbc:mysql://"+SystemSettings.sql_ip_port+"/" + SystemSettings.sql_schema + "?" + "user=" + SystemSettings.sql_master_user + "&password=" + SystemSettings.sql_master_pw);
    }

    public void init(boolean scrollable) throws ClassNotFoundException, SQLException {
        if (connection==null){

            initConnection();

            if (scrollable){
                if (prepareSql){
                    preparedStatement = connection.prepareStatement(query,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
                    assignParameters();
                }else{
                    statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
                }
            }else{
                if (prepareSql){
                    preparedStatement = connection.prepareStatement(query);
                    assignParameters();
                }else{
                    statement = connection.createStatement();
                }
            }

        }
    }

    private void assignParameters() throws SQLException {
        //loop through parameters
        //assign parameters to query

        for(int i =0; i<parameters.length;i++){
            preparedStatement.setObject(i+1,parameters[i]);
        }
    }

    public ResultSet getResultSet() throws SQLException, NoRecordFoundException {
        ResultSet resultSet;
        if (prepareSql){
            resultSet = preparedStatement.executeQuery();
        }else{
            resultSet = statement.executeQuery(query);
        }

        this.resultSet = resultSet;
        if(!resultSet.isBeforeFirst())
            throw new NoRecordFoundException("No results found for ["+query+"]");

        return resultSet;
    }

    public void executeUpdate() throws SQLException {
        if (prepareSql){
            preparedStatement.executeQuery();
        }else{
            statement.executeUpdate(query);
        }
    }

    public void close() throws SQLException {
        if(resultSet != null){
            resultSet.close();
        }

        if (statement != null) {
            statement.close();
        }

        if (connection!= null) {
            connection.close();
        }
    }

    public void close(ResultSet resultSet) throws SQLException {

        if(resultSet!=null){
            resultSet.close();
        }

        if (statement != null) {
            statement.close();
        }

        if (connection!= null) {
            connection.close();
        }

    }
}
