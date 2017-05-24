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

	private static final String AMQ_DEFAULT_HOST = "localhost";
	private static final int AMQP_PORT = 5672;
	private static final String AMQP_DEFAULT_ADDRESS = "aTopic";

	private static final String BRIDGE_STATUS = "AMQP Bridge Status: %s";
	private static final String REST_STATUS = "REST API Status: %s";
	private static final String OK = "OK";
	private static final String NOT_OK = "Not OK";
	private static final String SENT = "Sent: \n %s";

	private String amqBrokerHost;
	private int amqBrokerPort;
	private String amqBrokerUsername;
	private String amqBrokerPassword;
	private String amqBrokerAddress;

	private boolean amqpOnline = false;
	private boolean restOnline = false;

	private HttpServer server;
	private AmqpBridge bridge;
	MessageProducer<JsonObject> producer = null;

	public RESTServiceVerticle() {
		amqBrokerHost = System.getenv(CommonConstants.AMQP_BROKER_HOST_ENV) != null
				? System.getenv(CommonConstants.AMQP_BROKER_HOST_ENV) : AMQ_DEFAULT_HOST;
		amqBrokerPort = System.getenv(CommonConstants.AMQP_BROKER_PORT_ENV) != null
				? Integer.parseInt(System.getenv(CommonConstants.AMQP_BROKER_PORT_ENV)) : AMQP_PORT;
		amqBrokerAddress = System.getenv(CommonConstants.AMQP_BROKER_ADDRESS_ENV) != null
				? System.getenv(CommonConstants.AMQP_BROKER_ADDRESS_ENV) : AMQP_DEFAULT_ADDRESS;
		amqBrokerUsername = System.getenv(CommonConstants.AMQP_BROKER_USER_ENV);
		amqBrokerPassword = System.getenv(CommonConstants.AMQP_BROKER_PASSWORD_ENV);

	}

	@Override
	public void start(Future<Void> future) throws Exception {
		Router router = Router.router(vertx);
		bridge = AmqpBridge.create(vertx);

		HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx).register("server-online",
				fut -> fut.complete(((amqpOnline && restOnline) == true) ? Status.OK() : Status.KO()));

		router.get("/api/health/readiness").handler(rc -> rc.response().end(OK));
		router.get("/api/health/liveness").handler(healthCheckHandler);
		router.get("/api/publish").handler(this::publishData);

		server = vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port", 8080),
				ar -> {
					if (ar.succeeded()) {
						log.info(String.format(REST_STATUS, OK));
						restOnline = ar.succeeded();
					} else {
						log.error(String.format(REST_STATUS, ar.cause().getMessage()));
					}
				});

		bridge.start(amqBrokerHost, amqBrokerPort, amqBrokerUsername, amqBrokerPassword, res -> {
			if (res.succeeded()) {

				amqpOnline = res.succeeded();
				// Set up a producer using the bridge
				producer = bridge.createProducer(amqBrokerAddress);

				log.info(String.format(BRIDGE_STATUS, OK));

			} else {

				log.error(String.format(BRIDGE_STATUS, res.cause().getMessage()));

			}

		});

	}

	/**
	 * Send the data to AMQ
	 * 
	 * @param rc
	 */
	private void publishData(RoutingContext rc) {
		if ((restOnline && amqpOnline) != true) {
			this.error(rc, NOT_OK);
			return;
		}

		String data = rc.request().getParam("data");

		try {

			JsonObject amqpMsgPayload = new JsonObject();
			amqpMsgPayload.put("body", data);

			producer.send(amqpMsgPayload);

			log.info(String.format(SENT, amqpMsgPayload.encodePrettily()));

			rc.response().setStatusCode(200).putHeader(CONTENT_TYPE, "text/plain").end(OK);

		} catch (Exception e1) {

			log.error("Oops", e1);
			this.error(rc, e1.getMessage());

		}
	}

	private void error(RoutingContext rc, String message) {
		rc.response().setStatusCode(400).putHeader(CONTENT_TYPE, "text/plain").end(message);
	}

}
