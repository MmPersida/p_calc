package com.persida.pathogenicity_calculator.utils;

import com.persida.pathogenicity_calculator.utils.constants.Constants;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    private static Logger logger = Logger.getLogger(DateUtils.class);
    private static String timeFormat = "yyyy-MM-dd'T'hh:mm:ss.SSSZ";

    public static Date stringToDateParser(String dateStr){
        try {
            DateFormat df = new SimpleDateFormat(timeFormat, Locale.ENGLISH);
            Date date = df.parse(dateStr);
            return date;
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
            return null;
        }
    }

    public static String dateToStringParser(Date date){
        if(date == null){
            return null;
        }
        try {
            //example: "2025-04-09T06:50:11.725Z"
            /*
            DateFormat df = new SimpleDateFormat(timeFormat);
            String strDate = df.format(date);*/
            String strDate = date.toInstant().toString(); //using time library
            return strDate;
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
            return null;
        }
    }

    public static String localDateToStringParser(LocalDateTime localDate){
        if(localDate == null){
            return null;
        }
        try {
            LocalDateTime localDateRef = LocalDateTime.now();//For reference
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timeFormat, Locale.ENGLISH);
            return localDateRef.format(formatter);
        }catch(Exception e){
            logger.error(e);
            return null;
        }
    }

    public static LocalDateTime stringToLocalDateParserParser(String dateStr){
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timeFormat, Locale.ENGLISH);
            return LocalDateTime.parse(dateStr, formatter);
        }catch(Exception e){
            logger.error(e);
            return null;
        }
    }

    public static Date addDates(String startDateStr, int periodType, int period){
        try {
            DateFormat df = new SimpleDateFormat(timeFormat, Locale.ENGLISH);
            Calendar c = Calendar.getInstance();
            c.setTime(df.parse(startDateStr));
            c.add(periodType, period);
            return c.getTime();
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
            return null;
        }
    }

    public static String getTheTimeFromMillis(long timeInMilliSeconds){
        long seconds = timeInMilliSeconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        String timespanRemaining = "";
        if(days >= 1){
            if(days > 1){
                timespanRemaining = timespanRemaining + days + " days ";
            }else{
                timespanRemaining = timespanRemaining + days + " day ";
            }
        }
        if(hours >= 1){
            if(hours > 1){
                timespanRemaining = timespanRemaining + (hours % 24) + " hours ";
            }else{
                timespanRemaining = timespanRemaining + (hours % 24) + " hour ";
            }
        }
        if(minutes >= 1){
            if(minutes > 1){
                timespanRemaining = timespanRemaining + (minutes % 60) + " minutes ";
            }else{
                timespanRemaining = timespanRemaining + (minutes % 60) + " minute ";
            }
        }
        return timespanRemaining;
    }
}
