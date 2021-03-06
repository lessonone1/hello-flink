package com.github.myetl.flink.inoutput;

import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.formats.json.JsonRowDeserializationSchema;
import org.apache.flink.formats.json.JsonRowSerializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.KafkaTableSink;
import org.apache.flink.streaming.connectors.kafka.KafkaTableSource;
import org.apache.flink.streaming.connectors.kafka.config.StartupMode;
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

/**
 * kafka Table Api方式读写数据
 */
public class KafkaTableApi {

    public static void main(String[] args) throws Exception {
        // 运行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment
                .createLocalEnvironmentWithWebUI(new Configuration());

        env.setParallelism(1);
        // 使用Blink
        EnvironmentSettings bsSettings = EnvironmentSettings.newInstance()
                .useBlinkPlanner()
                .inStreamingMode()
                .build();
        // table 环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, bsSettings);

        // kafka 连接信息
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "127.0.0.1:9092");
        properties.setProperty("group.id", "KafkaTableApi");

        // 定义表结构
        TableSchema schema = TableSchema.builder()
                .field("name", DataTypes.STRING())
                .field("age", DataTypes.INT())
                .field("class", DataTypes.STRING())
                .build();

        // 1. 输入 这里的参数都是通过构造函数传入 使用起来较为复杂 推荐使用connect 和 ddl方式注册
        KafkaTableSource input = new KafkaTableSource(
                schema,
                Optional.empty(),
                Collections.emptyList(),
                Optional.empty(),
                "student",
                properties,
                new JsonRowDeserializationSchema
                        .Builder(
                        new RowTypeInfo(schema.getFieldTypes(), schema.getFieldNames()))
                        .failOnMissingField()
                        .build(),
                StartupMode.EARLIEST,
                Collections.emptyMap());

        // 注册
        tableEnv.registerTableSource("student", input);

        Optional<FlinkKafkaPartitioner<Row>> partitioner = Optional.empty();

        // 2. 输出
        KafkaTableSink output = new KafkaTableSink(
                schema,
                "stuout",
                properties,
                partitioner,
                new JsonRowSerializationSchema.Builder(new RowTypeInfo(
                        schema.getFieldTypes(), schema.getFieldNames()))
                        .build());
        // 注册
        tableEnv.registerTableSink("stuout", output);

        // 3. Table Api 方式 处理 数据 等同与下面的sql
        tableEnv
                .scan("student")
                .filter("age > 16")
                .insertInto("stuout");

        // 3. sql 方式处理数据
//        tableEnv.sqlUpdate("insert into stuout select name, age, class from student where age > 16");


        env.execute("KafkaTableApi");
    }
}
