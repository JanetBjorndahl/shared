/*
 * Copyright 2012 Foundation for On-Line Genealogy, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.werelate.test;

import org.werelate.dq.PersonDQAnalysis;
import org.werelate.dq.FamilyDQAnalysis;
import org.werelate.util.SharedUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.io.StringReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.net.URLDecoder;
import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.ParsingException;
import nu.xom.Document;

/**
 * This class tests DQ analysis functionality used by the wiki
 * User: DataAnalyst
 * Date: Dec 2022
 */
public class TestDQAnalysis {
   
   private static final Logger logger = LogManager.getLogger("org.werelate.dq");

   public static void main(String[] args) throws IOException, ParsingException {
      int tests;
      boolean testResult;
      String[] testData = new String[4]; 
  
      try {
         File testDataFile = new File("../data/testDQ.txt");
         Scanner testReader = new Scanner(testDataFile);
         testReader.nextLine();     // get and ignore first line - column headings
  
         tests = 0;
         while ( testReader.hasNextLine()) {
            tests++;
            String testLine = testReader.nextLine();
            // [0] = structured content XML; [1] = namespace; [2] = page title; [3] = child title
            testData = testLine.split("\\t", -1);

            String data = URLDecoder.decode(testData[0], "UTF-8");
            // Get rid of extra quotation marks in test data (at start and end of string and doubled for each internal quotation mark)
            data = data.substring(1, data.length()-1).replace("\"\"", "\"");
logger.debug("data=" + data);
            
            Element root = SharedUtils.parseText(new Builder(), data, true).getRootElement();

            if (testData[1].equals("Person")) {
               // Determine dates and issues
               PersonDQAnalysis personDQAnalysis = new PersonDQAnalysis(root, testData[2]);

            // Family page
            }
            else {
               // Determine dates and issues
               FamilyDQAnalysis familyDQAnalysis = new FamilyDQAnalysis(root, testData[2], testData[3]);
            }
         }   
      } catch (FileNotFoundException e) {
         System.out.println("Input file not found");
      }
   }
}
