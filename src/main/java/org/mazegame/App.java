package org.mazegame;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	

//    	DBGenerator.deleteDB();
    	
    	DBGenerator.getRoomDetail(1);
    	
    	DBGenerator.getDoorDetail(1, DBGenerator.Direction.BOTTOM);
//    	
    	DBGenerator.answerQuestion(29, "1");
//    	
    	DBGenerator.getDoorDetail(1, DBGenerator.Direction.BOTTOM);
    	
    };
}
