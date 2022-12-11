package org.werelate.test;

import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.util.Scanner; // Import the Scanner class to read text files

import org.werelate.util.EventDate;

public class TestEventDate {

  public static void main(String[] args) {
    int tests;
    EventDate testDate;
    boolean testResult;
    String[] testData = new String[13]; 

    try {
      File testDataFile = new File("../data/testdates.txt");
      Scanner testReader = new Scanner(testDataFile);
      testReader.nextLine();     // get and ignore first two lines - column headings
      testReader.nextLine();    

      tests = 0;
      while ( testReader.hasNextLine()) {
        tests++;
        String testLine = testReader.nextLine();
        testData = testLine.split("\\t", -1);

    // [0] = date; [1] = formatedDate; [2] = return/message; [3] = return/reformat details; [4] - ignore; 
    // [5] = dateKey; [6], [7] - ignore; [8] = effectiveYear; [9] = earliestYear; [10] = latestYear; [11] = onlyYear;
    // [12] = minDay; [13] = maxDay;
    // also acceptable values: [14] = return/message; [15] = dateKey; [16] = effectiveYear;
    // 
// System.out.println("test #" + tests + ": date=" + testData[0] )    ;
        testDate = new EventDate(testData[0]);
        testDate.getFormatedDate();        // to ensure that getting formated date multiple times works
        testResult = testDate.editDate();
        if ( (testData[2].equals("1") && testResult == false) || 
             (!testData[2].equals("1") && 
             (testResult == true || 
             ! (testDate.getErrorMessage().equals(testData[2]) || testDate.getErrorMessage().equals(testData[14])) ))) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; return=" + testResult + "; message=" + testDate.getErrorMessage() + "; expected return=" + testData[2] + ";");
        }
        if ( (testData[3].equals("1") && testDate.getSignificantReformat()) ||         // expected result="1" means no significant reformating
             (testData[3].equals("Significant reformat") && !testDate.getSignificantReformat()) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; reformat details=" + testDate.getSignificantReformat() + "; expected=" + testData[3] + ";");
        }
        if ( !testDate.getFormatedDate().equals(testData[1]) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; formated=" + testDate.getFormatedDate() + "; expected formated=" + testData[1] + ";");
        }
        if ( testDate.getDateSortKey()!=(testData[5].equals("") ? 0 : Integer.parseInt(testData[5])) &&
              testDate.getDateSortKey()!=(testData[15].equals("") ? 0 : Integer.parseInt(testData[15])) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
            "; sort key=" + testDate.getDateSortKey() + "; expected sort key=" + testData[5] + ";");
        }
        if ( (testDate.getEffectiveYear() == null && ! (testData[8].equals("") || testData[16].equals(""))) ||
              (testDate.getEffectiveYear() != null && 
              ! (testDate.getEffectiveYear().equals(testData[8]) || testDate.getEffectiveYear().equals(testData[16]))) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; effective year=" + testDate.getEffectiveYear() + "; expected=" + testData[8] + ";");
        }
        
        if ( (testDate.getEarliestYear() == null && !testData[9].equals("")) ||
              (testDate.getEarliestYear() != null && 
              (testData[9].equals("") || !testDate.getEarliestYear().equals(Integer.valueOf(testData[9])))) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; earliest year=" + testDate.getEarliestYear() + "; expected=" + testData[9] + ";");
        }

        if ( (testDate.getLatestYear() == null && !testData[10].equals("")) ||
              (testDate.getLatestYear() != null && 
              (testData[10].equals("") || !testDate.getLatestYear().equals(Integer.valueOf(testData[10])))) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; latest year=" + testDate.getLatestYear() + "; expected=" + testData[10] + ";");
        }

        if ( testDate.getMinDay()!=(testData[12].equals("") ? 0 : Integer.parseInt(testData[12])) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; min day=" + testDate.getMinDay() + "; expected=" + testData[12] + ";");
        }
        
        if ( testDate.getMaxDay()!=(testData[13].equals("") ? 0 : Integer.parseInt(testData[13])) ) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; max day=" + testDate.getMaxDay() + "; expected=" + testData[13] + ";");
        }

        /* only needed this test to ensure the uncertainSplitYear code was working - expected output isn't part of testdates input file
        if ( testDate.uncertainSplitYear()) {
          System.out.println("test #" + tests + ": date=" + testData[0] + 
              "; reformat details=" + testDate.getSignificantReformat() + "; uncertain split year;");
        }
        */
      }
      System.out.println("Number of dates tested: " + tests);

      testReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("File not found");
      e.printStackTrace();
    }
    
  }
 }