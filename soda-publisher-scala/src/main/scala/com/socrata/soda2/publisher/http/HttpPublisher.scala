package com.socrata.soda2.publisher.http

import com.ning.http.client.AsyncHttpClient

import com.socrata.soda2.consumer.http.{LowLevelHttp, HttpConsumer}
import com.socrata.soda2.publisher.Publisher
import com.socrata.http.{NoAuth, Authorization}
import com.socrata.future.ExecutionContext

class HttpPublisher(lowLevel: LowLevelHttp) extends HttpConsumer(lowLevel) with Publisher {
  def this(client: AsyncHttpClient, host: String, port: Int = 443, authorization: Authorization = NoAuth)(implicit executionContext: ExecutionContext) =
    this(new LowLevelHttp(client, host, port, authorization))
}
