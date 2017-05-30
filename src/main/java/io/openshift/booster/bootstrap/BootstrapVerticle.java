package io.openshift.booster.bootstrap;

import io.openshift.booster.messaging.AMQProducerVerticle;
import io.openshift.booster.service.RESTServiceVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

public class BootstrapVerticle extends AbstractVerticle {

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(RESTServiceVerticle.class.getName());
		vertx.deployVerticle(AMQProducerVerticle.class.getName());
	}

}
