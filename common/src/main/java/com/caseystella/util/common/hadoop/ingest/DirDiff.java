package com.caseystella.util.common.hadoop.ingest;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.apache.commons.cli.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import javax.annotation.Nullable;
import java.io.*;
import java.util.*;

/**
 * Created by cstella on 9/6/14.
 */
public class DirDiff {
    private static enum Opts
    {
         HELP(OptionBuilder.withLongOpt("help")
                          .withDescription("Print this message")
                          .create("h")
             , "h"
             )
        ,CONFIG(OptionBuilder.hasArg()
                             .withArgName("file")
                             .withDescription("Config JSON file, conforming to the expectations of the class")
                             .withLongOpt("config")
                             .isRequired()
                             .create("f")
               , "f"
               )
        ;
        static Options options = new Options();
        static CommandLineParser parser = new PosixParser();
        static
        {
            for(Opts opt : values())
            {
                options.addOption(opt.option);
            }
        }
        String code;
        Option option;
        Opts(Option option, String code)
        {
            this.option = option;
            this.code = code;
        }
        public boolean has(CommandLine commandLine)
        {
            return commandLine.hasOption(code);
        }
        public String get(CommandLine commandLine)
        {
            return commandLine.getOptionValue(code);
        }
        public static void printHelp(PrintWriter pw)
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "DirDiff", options , true);

            formatter.printHelp(pw, HelpFormatter.DEFAULT_WIDTH
                               , "DirDiff"
                               , null, options
                               , HelpFormatter.DEFAULT_LEFT_PAD
                               , HelpFormatter.DEFAULT_DESC_PAD
                               , null
                               , true
                               );

        }
        public static CommandLine parse(String... argv)
        {

            try
            {
                return parser.parse( options, argv );
            }
            catch(ParseException ex)
            {
                printHelp(new PrintWriter(System.err));
                return null;
            }
        }
    }
    public static void main(String... argv) throws IOException {
        File configFile;
        Config config;
        CommandLine line = Opts.parse(argv);
        if(line == null)
        {
            System.exit(-1);
        }
        if(Opts.HELP.has(line))
        {
            Opts.printHelp(new PrintWriter(System.out));
            return;
        }
        {
            configFile = new File(Opts.CONFIG.get(line));
            try {
                config = Config.load(new FileReader(configFile));
                config.initialize();
            } catch (Throwable e) {
                System.err.println("Unable to load config file");
                e.printStackTrace(System.err);
                System.exit(-1);
                return;
            }
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Map<String, Map.Entry<Config.Mapping, Set<String>>  > sourcePathToHDFS = new LinkedHashMap<String, Map.Entry<Config.Mapping,Set<String>>>();
        for(Config.Mapping mapping : config.getMappings())
        {
            File sourceDir = new File(mapping.getSource());
            if(!sourceDir.isDirectory())
            {
                continue;
            }
            Set<String> hdfsFiles = null;
            try {
                hdfsFiles = lsHDFS(mapping.getDestination());
            }
            catch(FileNotFoundException fnfe)
            {
                hdfsFiles = new HashSet<String>();
            }
            catch (IOException e) {
                continue;
            }
            sourcePathToHDFS.put(sourceDir.getCanonicalPath(), new AbstractMap.SimpleEntry<Config.Mapping, Set<String>>(mapping,hdfsFiles));
        }
        for(String fileS = null; (fileS = reader.readLine()) != null;)
        {
            File inFile = new File(fileS);
            if(!inFile.exists())
            {
               continue;
            }
            String canonicalPath = inFile.getCanonicalPath();
            for(Map.Entry<String, Map.Entry<Config.Mapping, Set<String>>> e : sourcePathToHDFS.entrySet())
            {
                if(canonicalPath.startsWith(e.getKey()))
                {

                    Predicate<File> pred = Predicates.and(config, Predicates.and(e.getValue().getKey(), new PrintFile(e.getValue().getValue())));
                    if(pred.apply(inFile))
                    {
                        try {
                            System.out.println("\"" + inFile.getCanonicalPath() + "\" \"" + e.getValue().getKey().getDestination() + "\"");
                        } catch (IOException ioe) {
                            throw new RuntimeException("Cannot canonicalize " + inFile, ioe);
                        }
                    }
                    break;
                }
            }
        }
    }

    private static class PrintFile implements Predicate<File>
    {
        Set<String> hdfsFiles;
        PrintFile(Set<String> hdfsFiles)
        {
           this.hdfsFiles = hdfsFiles;
        }

        @Override
        public boolean apply(@Nullable File input) {
            return !hdfsFiles.contains(input.getName());
        }
    }

    private static Set<String> lsHDFS(String location) throws IOException {
        FileSystem fs = FileSystem.newInstance(new Configuration());
        Set<String> ret = new HashSet<String>();
        for(FileStatus stat : fs.listStatus(new Path(location)))
        {
            ret.add(stat.getPath().getName());
        }
        return ret;
    }
}
