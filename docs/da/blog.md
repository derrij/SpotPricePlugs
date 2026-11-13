---
layout: default
lang: da
home: /da/
blog: /da/blog/
title: Blog
description: "Praktiske guides om Nord Pool-priser, smarte stik og rigtig besparelse."
permalink: /da/blog/
---

# Blog

Praktiske guides om Nord Pool-priser, smarte stik og rigtig besparelse.

{% assign loc = site.posts | where: "lang", "da" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Flere guides (på engelsk)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
