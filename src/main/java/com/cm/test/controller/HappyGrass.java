package com.cm.test.controller;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * @Author CM大都督
 * @Date 2021/1/30 16:16
 * @Description
 *
 * 获取酷狗网页版歌单批量下载链接
 * 注意：仅对网页版有效，某些需要客户端才能听的无效
 */
@RestController
public class HappyGrass {

    /**
     * 1. 配置文件中的  kglisturl是歌单的链接
     * 2. 配置文件中的  kglisturl2是固定不变的（暂时）
     */
    @Value("${TestHttpClient.doGetHttp.kglisturl}")
    private String url;
    @Value("${TestHttpClient.doGetHttp.kglisturl2}")
    private String url2;

    /**
     * 构造请求url获取
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/happygrass")
    public void doGetHttp(HttpServletResponse httpresponse) throws IOException, ExecutionException, InterruptedException {

        long starttime = System.currentTimeMillis();

        //存储中间数据
        HashMap<String,String> songidmap = new HashMap<String,String>();
        HashMap<String,String> songurlmap = new HashMap<String,String>();

        /**
         * 模拟浏览器行为
         * 1. 与浏览器建立链接
         * 2. Get方法执行歌单url：https://www.kugou.com/yy/special/single/3439078.html
         * 3. 设置连接信息
         * 4. 执行得到结果
         * 5. 分析结果获取歌曲名和id
         * 6. 存入map
         */
        CloseableHttpClient closeableHttpClient = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response = null;

        // 配置信息
        RequestConfig requestConfig = RequestConfig.custom()
                // 设置连接超时时间(单位毫秒)
                .setConnectTimeout(50000)
                // 设置请求超时时间(单位毫秒)
                .setConnectionRequestTimeout(50000)
                // socket读写超时时间(单位毫秒)
                .setSocketTimeout(50000)
                // 设置是否允许重定向(默认为true)
                .setRedirectsEnabled(true).build();
        // 将上面的配置信息 运用到这个Get请求里
        httpGet.setConfig(requestConfig);

        //执行获得返回结果，取出需要的歌单   歌曲名和歌曲id
        response = closeableHttpClient.execute(httpGet);
        HttpEntity responseEntity = response.getEntity();
        System.out.println("响应状态："+response.getStatusLine());

        if(responseEntity!=null){

            String txt = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            Document document = Jsoup.parse(txt);
            Element songs = document.getElementById("songs");
            Elements songlist = songs.getElementsByTag("a");
            for (Element element : songlist.subList(1,songlist.size())) {

                String songname = element.attr("title");
                //获取歌曲id
                Elements songids = element.getElementsByTag("input");
                for (Element songid : songids) {
                    String mapsongid = songid.attr("id").substring(4);
                    songidmap.put(mapsongid,songname);
                }
            }
        }

        /**
         * 1. 上一步拿到歌曲id，构建url2，把id作为hash参数进行拼接
         *      url2&hash=‘id’
         * 2. 再次执行，可获得相册id
         * 3. 将相册id作为album_id参数在url2的基础上构建url3
         *      url2&album_id=‘相册id’
         * 4. 执行，可获得音乐mp3下载url
         *      https://webfs.yun.kugou.com/202101311301/8f73aa2116bbb1190dde113d8bdee3fd/G227/M07/08/15/w5QEAF7y-HyAdBK2ABk9EBLJ8ao101.mp3
         * 5. 保存map
         */

        ExecutorService executorService = Executors.newCachedThreadPool();//启用多线程  有请求就开一个

        for (Map.Entry<String,String> entry : songidmap.entrySet()) {

            Future<Map<String,String>> future = executorService.submit(new Callable<Map<String,String>>() {
                @Override
                public Map call() {
                    try {
                    String album_url = url2 + "&hash=" + entry.getKey();
                    HttpGet httpGet2 = new HttpGet(album_url);
                    CloseableHttpResponse response2 = null;
                    httpGet2.setConfig(requestConfig);
                    //执行
                    response2 = closeableHttpClient.execute(httpGet2);
                    HttpEntity responseEntity2 = response2.getEntity();
                    //返回json  取值 39769503
                    if (responseEntity2 != null) {

                        String demo = EntityUtils.toString(responseEntity2, StandardCharsets.UTF_8).replace(");", "");
                        String json = demo.substring(42);
                        JSONObject jsonObject = JSONObject.parseObject(json);
                        JSONObject data = jsonObject.getJSONObject("data");

                        //更新map
                        String album_id = data.getString("album_id");
                        String musicurl = album_url + "&album_id=" + album_id;
                        HttpGet httpGet3 = new HttpGet(musicurl);

                        CloseableHttpResponse response3 = null;
                        // 将上面的配置信息 运用到这个Get请求里
                        httpGet3.setConfig(requestConfig);
                        //执行
                        response3 = closeableHttpClient.execute(httpGet3);
                        HttpEntity responseEntity3 = response3.getEntity();
                        //返回json  取值 39769503
                        if (responseEntity3 != null) {

                            String demo2 = EntityUtils.toString(responseEntity3, StandardCharsets.UTF_8).replace(");", "");
                            String musicjson = demo2.substring(42);
                            JSONObject musicjsonObject = JSONObject.parseObject(musicjson);
                            JSONObject musicdata = musicjsonObject.getJSONObject("data");
                            //更新map
                            String song_url = musicdata.getString("play_url");
                            String songname = entry.getValue();
                            System.out.println(songname+"---"+song_url);

                            synchronized (this){
                                songurlmap.put(songname, song_url);
                            }
                        }
                    }
                    }catch (Exception exception){

                    }
                    return songurlmap;
                }
            });
            songurlmap.putAll(future.get());
        }
        System.out.println(songurlmap);
        long midtime = System.currentTimeMillis();

        /**
         *  前端渲染或者生成html,结合IDM下载
         */
        String libody = "仅用于学习用途，切勿用作违法行为！-------------- Design By CM大都督（快乐小草）</br>";
        for (Map.Entry<String,String> entry : songurlmap.entrySet()) {
            String sn = entry.getKey();
            String su = entry.getValue();
            String text = sn+"----"+su;
            libody = libody + "</br><li style = \"font-size:14px\">\n" + sn+"</br>"+
                    "\t  <a style = \"text-decoration:none;color:#01A9DB\" title=\n" + su+
                    "\t  href=" + su+
                    "\t  >" +su+
                    "</a></li>";
        }
        String result = "<div id=\"songs\" class=\"list1\">\n" +
                "\t<ol>                           \n" +
                libody+
                "\t</ol>\n" +
                "</div>";

        httpresponse.setContentType("text/html;charset=UTF-8");
        PrintWriter out = httpresponse.getWriter();
        out.write(result);

        long endTime = System.currentTimeMillis();

        System.out.println(songidmap.size()+"收集花费时间："+(midtime-starttime));
        System.out.println(songidmap.size()+"前端花费时间："+(endTime-midtime));
        System.out.println(songidmap.size()+"总花费时间："+(endTime-starttime));

        //保存为本地HTML
    /*  String filename = "GET_"+url.replace("://","_").replace("/","_");
        Files.write(Paths.get("D:\\Java\\TEST\\"+filename), EntityUtils.toString(responseEntity, StandardCharsets.UTF_8).getBytes());*/
    }
}
