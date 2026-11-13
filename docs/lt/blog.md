---
layout: default
lang: lt
home: /lt/
blog: /lt/blog/
title: Tinklaraštis
description: "Praktiniai gidai apie Nord Pool kainas, išmaniąsias rozetes ir realų taupymą."
permalink: /lt/blog/
---

# Tinklaraštis

Praktiniai gidai apie Nord Pool kainas, išmaniąsias rozetes ir realų taupymą.

{% assign loc = site.posts | where: "lang", "lt" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Daugiau gidų (anglų k.)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
