package gui.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class JsonUtil {
    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    private static Gson gson = new GsonBuilder().create();

    /**
     * 从指定的 JSON 文件读取数据并解析为指定类型的对象
     *
     * @param filePath JSON 文件路径
     * @param clazz    要解析的对象类型
     * @param <T>     泛型类型
     * @return 解析后的对象
     */
    public static <T> T readJsonFromFile(String filePath, Class<T> clazz) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return gson.fromJson(br, clazz);
        } catch (IOException e) {
            log.info("Failed to read object from file: {}", filePath);
            return null; // 返回 null 或者可以抛出自定义异常
        }
    }

    /**
     * 将对象写入到指定的 JSON 文件
     *
     * @param filePath JSON 文件路径
     * @param object   要写入的对象
     * @param <T>     泛型类型
     */
    public static <T> void writeJsonToFile(String filePath, T object) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            gson.toJson(object, bw);
        } catch (IOException e) {
            log.info("Failed to write object to file: {}", filePath);
        }
    }
}
