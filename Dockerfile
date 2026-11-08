FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY Admin Admin
COPY Landlord Landlord
COPY HomePage HomePage
COPY UserDashboard UserDashboard
COPY createAccount createAccount
COPY META-INF META-INF
COPY index.html style.css portal-identity.js csrf.js experience.js experience.css property-gallery.js property-gallery.css maps.js maps.css system-status.js ./
RUN mvn -B -DskipTests package

FROM tomcat:9-jdk11-temurin
RUN groupadd --gid 10001 uninest && useradd --uid 10001 --gid uninest --create-home uninest \
    && mkdir -p /data/uploads && chown -R uninest:uninest /data /usr/local/tomcat
COPY --from=build --chown=uninest:uninest /app/target/uninest.war /usr/local/tomcat/webapps/uninest.war

# Redirect visitors from / to /uninest/
RUN mkdir -p /usr/local/tomcat/webapps/ROOT \
    && printf '%s\n' '<% response.sendRedirect("/uninest/"); %>' \
       > /usr/local/tomcat/webapps/ROOT/index.jsp \
    && chown -R uninest:uninest /usr/local/tomcat/webapps/ROOT

ENV UNINEST_UPLOAD_DIR=/data/uploads
USER uninest
EXPOSE 8080
CMD ["catalina.sh", "run"]
