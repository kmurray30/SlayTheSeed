package com.badlogic.gdx.net;

import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;
import com.badlogic.gdx.utils.async.AsyncExecutor;
import com.badlogic.gdx.utils.async.AsyncTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class NetJavaImpl {
   private final AsyncExecutor asyncExecutor = new AsyncExecutor(1);
   final ObjectMap<Net.HttpRequest, HttpURLConnection> connections = new ObjectMap<>();
   final ObjectMap<Net.HttpRequest, Net.HttpResponseListener> listeners = new ObjectMap<>();

   public void sendHttpRequest(final Net.HttpRequest httpRequest, final Net.HttpResponseListener httpResponseListener) {
      if (httpRequest.getUrl() == null) {
         httpResponseListener.failed(new GdxRuntimeException("can't process a HTTP request without URL set"));
      } else {
         try {
            String method = httpRequest.getMethod();
            URL url;
            if (method.equalsIgnoreCase("GET")) {
               String queryString = "";
               String value = httpRequest.getContent();
               if (value != null && !"".equals(value)) {
                  queryString = "?" + value;
               }

               url = new URL(httpRequest.getUrl() + queryString);
            } else {
               url = new URL(httpRequest.getUrl());
            }

            final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            final boolean doingOutPut = method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT");
            connection.setDoOutput(doingOutPut);
            connection.setDoInput(true);
            connection.setRequestMethod(method);
            HttpURLConnection.setFollowRedirects(httpRequest.getFollowRedirects());
            this.putIntoConnectionsAndListeners(httpRequest, httpResponseListener, connection);

            for (Entry<String, String> header : httpRequest.getHeaders().entrySet()) {
               connection.addRequestProperty(header.getKey(), header.getValue());
            }

            connection.setConnectTimeout(httpRequest.getTimeOut());
            connection.setReadTimeout(httpRequest.getTimeOut());
            this.asyncExecutor.submit(new AsyncTask<Void>() {
               public Void call() throws Exception {
                  try {
                     if (doingOutPut) {
                        String contentAsString = httpRequest.getContent();
                        if (contentAsString != null) {
                           OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());

                           try {
                              writer.write(contentAsString);
                           } finally {
                              StreamUtils.closeQuietly(writer);
                           }
                        } else {
                           InputStream contentAsStream = httpRequest.getContentStream();
                           if (contentAsStream != null) {
                              OutputStream os = connection.getOutputStream();

                              try {
                                 StreamUtils.copyStream(contentAsStream, os);
                              } finally {
                                 StreamUtils.closeQuietly(os);
                              }
                           }
                        }
                     }

                     connection.connect();
                     NetJavaImpl.HttpClientResponse clientResponse = new NetJavaImpl.HttpClientResponse(connection);

                     try {
                        Net.HttpResponseListener listener = NetJavaImpl.this.getFromListeners(httpRequest);
                        if (listener != null) {
                           listener.handleHttpResponse(clientResponse);
                        }

                        NetJavaImpl.this.removeFromConnectionsAndListeners(httpRequest);
                     } finally {
                        connection.disconnect();
                     }
                  } catch (Exception var31) {
                     Exception e = var31;
                     connection.disconnect();

                     try {
                        httpResponseListener.failed(e);
                     } finally {
                        NetJavaImpl.this.removeFromConnectionsAndListeners(httpRequest);
                     }
                  }

                  return null;
               }
            });
         } catch (Exception var13) {
            Exception e = var13;

            try {
               httpResponseListener.failed(e);
            } finally {
               this.removeFromConnectionsAndListeners(httpRequest);
            }
         }
      }
   }

   public void cancelHttpRequest(Net.HttpRequest httpRequest) {
      Net.HttpResponseListener httpResponseListener = this.getFromListeners(httpRequest);
      if (httpResponseListener != null) {
         httpResponseListener.cancelled();
         this.removeFromConnectionsAndListeners(httpRequest);
      }
   }

   synchronized void removeFromConnectionsAndListeners(Net.HttpRequest httpRequest) {
      this.connections.remove(httpRequest);
      this.listeners.remove(httpRequest);
   }

   synchronized void putIntoConnectionsAndListeners(Net.HttpRequest httpRequest, Net.HttpResponseListener httpResponseListener, HttpURLConnection connection) {
      this.connections.put(httpRequest, connection);
      this.listeners.put(httpRequest, httpResponseListener);
   }

   synchronized Net.HttpResponseListener getFromListeners(Net.HttpRequest httpRequest) {
      return this.listeners.get(httpRequest);
   }

   static class HttpClientResponse implements Net.HttpResponse {
      private final HttpURLConnection connection;
      private HttpStatus status;

      public HttpClientResponse(HttpURLConnection connection) throws IOException {
         this.connection = connection;

         try {
            this.status = new HttpStatus(connection.getResponseCode());
         } catch (IOException var3) {
            this.status = new HttpStatus(-1);
         }
      }

      @Override
      public byte[] getResult() {
         InputStream input = this.getInputStream();
         if (input == null) {
            return StreamUtils.EMPTY_BYTES;
         } else {
            byte[] var3;
            try {
               return StreamUtils.copyStreamToByteArray(input, this.connection.getContentLength());
            } catch (IOException var7) {
               var3 = StreamUtils.EMPTY_BYTES;
            } finally {
               StreamUtils.closeQuietly(input);
            }

            return var3;
         }
      }

      @Override
      public String getResultAsString() {
         InputStream input = this.getInputStream();
         if (input == null) {
            return "";
         } else {
            String var3;
            try {
               return StreamUtils.copyStreamToString(input, this.connection.getContentLength());
            } catch (IOException var7) {
               var3 = "";
            } finally {
               StreamUtils.closeQuietly(input);
            }

            return var3;
         }
      }

      @Override
      public InputStream getResultAsStream() {
         return this.getInputStream();
      }

      @Override
      public HttpStatus getStatus() {
         return this.status;
      }

      @Override
      public String getHeader(String name) {
         return this.connection.getHeaderField(name);
      }

      @Override
      public Map<String, List<String>> getHeaders() {
         return this.connection.getHeaderFields();
      }

      private InputStream getInputStream() {
         try {
            return this.connection.getInputStream();
         } catch (IOException var2) {
            return this.connection.getErrorStream();
         }
      }
   }
}
