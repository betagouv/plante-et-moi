FROM nginx

MAINTAINER Julien DAUPHANT

COPY nginx/ /etc/nginx/
COPY arles /var/www/html/arles
COPY dijon /var/www/html/dijon
COPY romainville /var/www/html/romainville

EXPOSE 8080
