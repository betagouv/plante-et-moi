FROM nginx

MAINTAINER Julien DAUPHANT

COPY nginx/ /etc/nginx/
COPY arles /var/www/html/arles
COPY dijon /var/www/html/dijon
COPY romainville /var/www/html/romainville
COPY malakoff /var/www/html/malakoff
COPY plantes /var/www/html/plantes
COPY amiens /var/www/html/amiens

EXPOSE 8080
