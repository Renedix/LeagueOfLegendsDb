package ApiAndDb.Db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created with IntelliJ IDEA.
 * User: EmilyWindows
 * Date: 3/6/15
 * Time: 7:10 AM
 * To change this template use File | Settings | File Templates.
 */
public class TransactionJdbcManager extends JdbcManager{

    public TransactionJdbcManager() throws ClassNotFoundException, SQLException {
        super();
    }

    public void startTransaction() throws ClassNotFoundException, SQLException {
        super.initConnection();
        this.connection.setAutoCommit(false);
    }


    public void executeQuery(String inputQuery) throws SQLException {
        statement = connection.createStatement();
        statement.execute(inputQuery);
        statement.close();
    }

    public void rollback() throws SQLException {
        this.connection.rollback();
    }

    public void commit() throws SQLException {
        this.connection.commit();
    }

    public void closeStatement() throws SQLException {
        this.statement.close();
    }


    public ResultSet getResultSetFromQuery(String inputQuery) throws SQLException, NoRecordFoundException {
        ResultSet resultSet;

        statement = connection.createStatement();

        resultSet = statement.executeQuery(inputQuery);

        this.resultSet = resultSet;
        if(!resultSet.isBeforeFirst())
            throw new NoRecordFoundException("No results found for ["+inputQuery+"]");

        return resultSet;
    }
}
