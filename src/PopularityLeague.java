import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class PopularityLeague extends Configured implements Tool {

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(new Configuration(), new PopularityLeague(), args);
        System.exit(res);
    }

    @Override
    public int run(String[] args) throws Exception {
        // TODO
    	Configuration conf = this.getConf();
        FileSystem fs = FileSystem.get(conf);
        Path tmpPath = new Path("/mp2/tmp");
        fs.delete(tmpPath, true);

        Job jobA = Job.getInstance(conf, "Page Count");
        jobA.setOutputKeyClass(IntWritable.class);
        jobA.setOutputValueClass(IntWritable.class);

        jobA.setMapperClass(LinkCountMap.class);
        jobA.setReducerClass(LinkCountReduce.class);

        FileInputFormat.setInputPaths(jobA, new Path(args[0]));
        FileOutputFormat.setOutputPath(jobA, tmpPath);

        jobA.setJarByClass(PopularityLeague.class);
        jobA.waitForCompletion(true);

        Job jobB = Job.getInstance(conf, "League Rank");
        jobB.setOutputKeyClass(IntWritable.class);
        jobB.setOutputValueClass(IntWritable.class);

        jobB.setMapOutputKeyClass(NullWritable.class);
        jobB.setMapOutputValueClass(IntArrayWritable.class);

        jobB.setMapperClass(LeagueRankMap.class);
        jobB.setReducerClass(LeagueRankReduce.class);
        jobB.setNumReduceTasks(1);

        FileInputFormat.setInputPaths(jobB, tmpPath);
        FileOutputFormat.setOutputPath(jobB, new Path(args[1]));

        jobB.setInputFormatClass(KeyValueTextInputFormat.class);
        jobB.setOutputFormatClass(TextOutputFormat.class);

        jobB.setJarByClass(PopularityLeague.class);
        return jobB.waitForCompletion(true) ? 0 : 1;
    }

    // TODO
    
    public static String readHDFSFile(String path, Configuration conf) throws IOException{
        Path pt=new Path(path);
        FileSystem fs = FileSystem.get(pt.toUri(), conf);
        FSDataInputStream file = fs.open(pt);
        BufferedReader buffIn=new BufferedReader(new InputStreamReader(file));

        StringBuilder everything = new StringBuilder();
        String line;
        while( (line = buffIn.readLine()) != null) {
            everything.append(line);
            everything.append("\n");
        }
        return everything.toString();
    }

    public static class IntArrayWritable extends ArrayWritable {
        public IntArrayWritable() {
            super(IntWritable.class);
        }

        public IntArrayWritable(Integer[] numbers) {
            super(IntWritable.class);
            IntWritable[] ints = new IntWritable[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                ints[i] = new IntWritable(numbers[i]);
            }
            set(ints);
        }
    }

    public static class LinkCountMap extends Mapper<Object, Text, IntWritable, IntWritable> {

    	@Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        	
        	String line = value.toString();
			StringTokenizer tokenizer = new StringTokenizer(line, " \t,;.?!-:@[](){}_*/");
			
			int i = 0;
			while (tokenizer.hasMoreTokens()){
				String nextToken = tokenizer.nextToken();
				
				if (i == 0)
					context.write(new IntWritable(Integer.parseInt(nextToken)), new IntWritable(0));
				else
					context.write(new IntWritable(Integer.parseInt(nextToken)), new IntWritable(1));
				
				i++;
			}
        }
    }

    public static class LinkCountReduce extends Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
 
    	@Override
        public void reduce(IntWritable key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
        	
        	int sum = 0;
			for(IntWritable val: values){
				sum += val.get();
			}
			
			context.write(key, new IntWritable(sum));        	
        }
    }

    public static class LeagueRankMap extends Mapper<Text, Text, NullWritable, IntArrayWritable> {

        List<String> league;

        @Override
        protected void setup(Context context) throws IOException,InterruptedException {
            Configuration conf = context.getConfiguration();

            String leaguePath = conf.get("league");

            this.league = Arrays.asList(readHDFSFile(leaguePath, conf).split("\n"));
        }

        
        private TreeSet<Pair<Integer, Integer>> countToPageMap = new TreeSet<Pair<Integer, Integer>>();
        
        @Override
        public void map(Text key, Text value, Context context) throws IOException, InterruptedException {

        	Integer count = Integer.parseInt(value.toString());
			Integer pageID = Integer.parseInt(key.toString());
			
			for (int i=0; i < league.size(); i++) {
				if (pageID == Integer.parseInt(league.get(i)))
					countToPageMap.add(new Pair<Integer, Integer>(count, pageID));
			}
			
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {

        	for(Pair<Integer, Integer> item : countToPageMap){
				Integer[] integers = {item.second, item.first};
				IntArrayWritable val = new IntArrayWritable(integers);
				context.write(NullWritable.get(), val);
			}
        }

    }

    public static class LeagueRankReduce extends Reducer<NullWritable, IntArrayWritable, IntWritable, IntWritable> {

        List<String> league;

        @Override
        protected void setup(Context context) throws IOException,InterruptedException {
            Configuration conf = context.getConfiguration();

            String leaguePath = conf.get("league");

            this.league = Arrays.asList(readHDFSFile(leaguePath, conf).split("\n"));
        }

        
        private TreeSet<Pair<Integer, Integer>> countToPageMap = new TreeSet<Pair<Integer, Integer>>();

        @Override
        public void reduce(NullWritable key, Iterable<IntArrayWritable> values, Context context) throws IOException, InterruptedException {

        	for (IntArrayWritable val: values) {
        		IntWritable[] pair= (IntWritable[]) val.toArray();
				
				Integer pageID = Integer.parseInt(pair[0].toString());
				Integer count =	Integer.parseInt(pair[1].toString());
				
				countToPageMap.add(new Pair<Integer, Integer>(count, pageID));
			}
			
        	int[] ID = new int[countToPageMap.size()];
        	int[] rank = new int[countToPageMap.size()];
        	int[] count = new int[countToPageMap.size()];
        	int i = 0;
        	
			for (Pair<Integer, Integer> item: countToPageMap) {
				
				ID[i] = item.second;
				count[i] = item.first;
				rank[i] = i;
				
				if(i!=0){
					if(count[i] == count[i-1]) {
						rank[i] = rank[i-1];
					}
				}
				context.write(new IntWritable(ID[i]), new IntWritable(rank[i]));
				
				i++;
			}
				
//				IntWritable ID = new IntWritable(item.second);
//				IntWritable value = new IntWritable(item.first);
//				context.write(ID, value);
				
//				for (int j=0; j < league.size(); j++) {
//					IntWritable pageID = new IntWritable(Integer.parseInt(league.get(j)));
//					
//					IntWritable value = null;
//					for(int k=0; k<league.size(); k++){
//						if(ID[k] == Integer.parseInt(league.get(j))){
//							value = new IntWritable(rank[k]);
//						}
//					}
//					
//					context.write(pageID, value);
//				}
				

				
			
        }
    }

}


class Pair<A extends Comparable<? super A>,
B extends Comparable<? super B>>
implements Comparable<Pair<A, B>> {

public final A first;
public final B second;

public Pair(A first, B second) {
this.first = first;
this.second = second;
}

public static <A extends Comparable<? super A>,
    B extends Comparable<? super B>>
Pair<A, B> of(A first, B second) {
return new Pair<A, B>(first, second);
}

@Override
public int compareTo(Pair<A, B> o) {
int cmp = o == null ? 1 : (this.first).compareTo(o.first);
return cmp == 0 ? (this.second).compareTo(o.second) : cmp;
}

@Override
public int hashCode() {
return 31 * hashcode(first) + hashcode(second);
}

private static int hashcode(Object o) {
return o == null ? 0 : o.hashCode();
}

@Override
public boolean equals(Object obj) {
if (!(obj instanceof Pair))
    return false;
if (this == obj)
    return true;
return equal(first, ((Pair<?, ?>) obj).first)
        && equal(second, ((Pair<?, ?>) obj).second);
}

private boolean equal(Object o1, Object o2) {
return o1 == o2 || (o1 != null && o1.equals(o2));
}

@Override
public String toString() {
return "(" + first + ", " + second + ')';
}
}
