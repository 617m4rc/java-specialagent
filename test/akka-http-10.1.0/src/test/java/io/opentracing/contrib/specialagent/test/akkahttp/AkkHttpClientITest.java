/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.test.akkahttp;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import io.opentracing.contrib.specialagent.TestUtil;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

public class AkkHttpClientITest {
  public static void main(final String[] args) throws Exception {
    final ActorSystem system = ActorSystem.create();
    final Materializer materializer = ActorMaterializer.create(system);

    Http http = null;
    // Use Reflection to call Http.get(system) because Scala Http class decompiles to java
    // class with 2 similar methods 'Http.get(system)' with difference in return type only
    for (final Method method : Http.class.getMethods()) {
      if (Modifier.isStatic(method.getModifiers()) && "get".equals(method.getName()) && Http.class.equals(method.getReturnType())) {
        http = (Http)method.invoke(null, system);
        break;
      }
    }

    final CompletionStage<HttpResponse> stage = http.singleRequest(HttpRequest.GET("http://www.google.com"));

    stage.whenComplete(new BiConsumer<HttpResponse, Throwable>() {
      @Override
      public void accept(HttpResponse httpResponse, Throwable throwable) {
        TestUtil.checkActiveSpan();
        System.out.println(httpResponse.status());
      }
    }).toCompletableFuture().get().entity().getDataBytes().runForeach(param -> {}, materializer);

    stage.thenRun(() -> {
      system.terminate();
    });

    TestUtil.checkSpan("akka-http-client", 1);
  }
}