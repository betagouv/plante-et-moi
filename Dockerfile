FROM nginx

MAINTAINER Julien DAUPHANT

COPY sites-enabled/*.conf /etc/nginx/sites-enabled/
COPY arles /var/www/html/arles
COPY dijon /var/www/html/dijon
COPY romainville /var/www/html/romainville

EXPOSE 8080
