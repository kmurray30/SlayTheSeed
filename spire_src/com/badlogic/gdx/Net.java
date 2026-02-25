package com.badlogic.gdx;

import com.badlogic.gdx.net.HttpStatus;
import com.badlogic.gdx.net.ServerSocket;
import com.badlogic.gdx.net.ServerSocketHints;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.utils.Pool;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Net {
   void sendHttpRequest(Net.HttpRequest var1, Net.HttpResponseListener var2);

   void cancelHttpRequest(Net.HttpRequest var1);

   ServerSocket newServerSocket(Net.Protocol var1, String var2, int var3, ServerSocketHints var4);

   ServerSocket newServerSocket(Net.Protocol var1, int var2, ServerSocketHints var3);

   Socket newClientSocket(Net.Protocol var1, String var2, int var3, SocketHints var4);

   boolean openURI(String var1);

   public interface HttpMethods {
      String GET = "GET";
      String POST = "POST";
      String PUT = "PUT";
      String DELETE = "DELETE";
   }

   public static class HttpRequest implements Pool.Poolable {
      private String httpMethod;
      private String url;
      private Map<String, String> headers;
      private int timeOut = 0;
      private String content;
      private InputStream contentStream;
      private long contentLength;
      private boolean followRedirects = true;
      private boolean includeCredentials = false;

      public HttpRequest() {
         this.headers = new HashMap<>();
      }

      public HttpRequest(String httpMethod) {
         this();
         this.httpMethod = httpMethod;
      }

      public void setUrl(String url) {
         this.url = url;
      }

      public void setHeader(String name, String value) {
         this.headers.put(name, value);
      }

      public void setContent(String content) {
         this.content = content;
      }

      public void setContent(InputStream contentStream, long contentLength) {
         this.contentStream = contentStream;
         this.contentLength = contentLength;
      }

      public void setTimeOut(int timeOut) {
         this.timeOut = timeOut;
      }

      public void setFollowRedirects(boolean followRedirects) throws IllegalArgumentException {
         if (!followRedirects && Gdx.app.getType() == Application.ApplicationType.WebGL) {
            throw new IllegalArgumentException("Following redirects can't be disabled using the GWT/WebGL backend!");
         } else {
            this.followRedirects = followRedirects;
         }
      }

      public void setIncludeCredentials(boolean includeCredentials) {
         this.includeCredentials = includeCredentials;
      }

      public void setMethod(String httpMethod) {
         this.httpMethod = httpMethod;
      }

      public int getTimeOut() {
         return this.timeOut;
      }

      public String getMethod() {
         return this.httpMethod;
      }

      public String getUrl() {
         return this.url;
      }

      public String getContent() {
         return this.content;
      }

      public InputStream getContentStream() {
         return this.contentStream;
      }

      public long getContentLength() {
         return this.contentLength;
      }

      public Map<String, String> getHeaders() {
         return this.headers;
      }

      public boolean getFollowRedirects() {
         return this.followRedirects;
      }

      public boolean getIncludeCredentials() {
         return this.includeCredentials;
      }

      @Override
      public void reset() {
         this.httpMethod = null;
         this.url = null;
         this.headers.clear();
         this.timeOut = 0;
         this.content = null;
         this.contentStream = null;
         this.contentLength = 0L;
         this.followRedirects = true;
      }
   }

   public interface HttpResponse {
      byte[] getResult();

      String getResultAsString();

      InputStream getResultAsStream();

      HttpStatus getStatus();

      String getHeader(String var1);

      Map<String, List<String>> getHeaders();
   }

   public interface HttpResponseListener {
      void handleHttpResponse(Net.HttpResponse var1);

      void failed(Throwable var1);

      void cancelled();
   }

   public static enum Protocol {
      TCP;
   }
}
