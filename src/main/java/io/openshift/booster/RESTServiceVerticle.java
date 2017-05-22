package io.openshift.booster;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openshift.common.CommonConstants;
import io.vertx.amqpbridge.AmqpBridge;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RESTServiceVerticle extends AbstractVerticle {

	private static final Logger log = LoggerFactory.getLogger("RESTServiceVerticle");

	private String amqBrokerHost;
	private String amqBrokerPort;
	private String amqBrokerUsername;
	private String amqBrokerPassword;

	private boolean online = false;
	private HttpServer server;
	private AmqpBridge bridge;

	public RESTServiceVerticle() {
		amqBrokerHost = System.getenv(CommonConstants.AMQP_BROKER_HOST);
		amqBrokerPort = System.getenv(CommonConstants.AMQP_BROKER_PORT);
		amqBrokerUsername = System.getenv(CommonConstants.AMQP_BROKER_USER);
		amqBrokerPassword = System.getenv(CommonConstants.AMQP_BROKER_PASSWORD);
	}

	@Override
	public void start(Future<Void> future) throws Exception {
		Router router = Router.router(vertx);
		bridge = AmqpBridge.create(vertx);

		HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx).register("server-online",
				fut -> fut.complete(online ? Status.OK() : Status.KO()));

		router.get("/api/health/readiness").handler(rc -> rc.response().end("OK"));
		router.get("/api/health/liveness").handler(healthCheckHandler);
		router.get("/api/publish").handler(this::publishData);

		bridge.start("localhost", 5672, "bholmes", "bholmes", res -> {
			log.info("Bridge Started");
		});

		server = vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port", 8080),
				ar -> {
					online = ar.succeeded();
					future.handle(ar.mapEmpty());
				});

	}

	/**
	 * Send the data to AMQ
	 * 
	 * @param rc
	 */
	private void publishData(RoutingContext rc) {
		if (!online) {
			this.error(rc, "Not OK");
			return;
		}

		String data = rc.request().getParam("data");

		MessageProducer<JsonObject> producer = null;
		log.info("Creating Producer");

		try {

			// Set up a producer using the bridge, send a message with it.
			producer = bridge.createProducer("aQueue");

			JsonObject amqpMsgPayload = new JsonObject();
			amqpMsgPayload.put("body", data);

			producer.send(amqpMsgPayload);

			log.info("Sent: \n" + amqpMsgPayload.encodePrettily());

			rc.response().setStatusCode(200).putHeader(CONTENT_TYPE, "text/plain").end("OK");

		} catch (Exception e1) {

			log.error("Oops", e1);
			this.error(rc, e1.getMessage());

		} finally {
			log.info("Closing Producer");
			producer.close();

		}
	}

	private void error(RoutingContext rc, String message) {
		rc.response().setStatusCode(400).putHeader(CONTENT_TYPE, "text/plain").end("Not OK");
	}

}
