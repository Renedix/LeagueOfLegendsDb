package ApiAndDb.Db;

public class NoRecordFoundException extends Exception {

    public String error;

    public NoRecordFoundException(){
        super();
    }

    public NoRecordFoundException(String err){
        super(err);
        this.error=err;
    }

}
